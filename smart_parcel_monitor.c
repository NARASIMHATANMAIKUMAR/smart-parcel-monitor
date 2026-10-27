#include "stm32f4xx.h"
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>

/*
 * Smart Parcel Condition Monitor
 * MCU: STM32F401CC (default HSI = 16 MHz)
 *
 * Existing wiring retained:
 * LCD + MPU6050: I2C1 PB6=SCL, PB7=SDA
 * LCD backpack address: 0x27, mapping P0=RS, P1=RW, P2=EN, P3=Backlight
 * MPU6050 address: 0x68 (AD0 low)
 * Reed switch digital output: PA0
 * HC-05: PA2=USART2_TX -> HC-05 RXD; PA3=USART2_RX <- HC-05 TXD
 *
 * IMPORTANT:
 * 1) I2C timing below assumes PCLK1 = 16 MHz.
 * 2) REED_TAMPER_LOGIC must match your module. Current working code uses HIGH=open.
 * 3) Thresholds are initial tuning values; test and adjust with the real parcel.
 */

#define I2C_TIMEOUT_CYCLES       50000UL
#define MPU6050_ADDR             0x68U
#define LCD_ADDR                 0x27U
#define REED_TAMPER_LOGIC        1U

/* Reed switch debounce: input must remain changed for this many loop samples. */
#define REED_DEBOUNCE_SAMPLES    4U

/*
 * MPU6050 is configured to +/-2g (16384 LSB/g).
 * Motion score = |delta X| + |delta Y| + |delta Z| between samples.
 * Initial thresholds: MEDIUM >= 2500, INTENSE >= 7000.
 * Tune these experimentally. These detect rapid change, not calibrated impact force.
 */
#define SHAKE_MEDIUM_THRESHOLD   2500L
#define SHAKE_INTENSE_THRESHOLD  7000L
#define SHAKE_CLEAR_THRESHOLD   1500L
#define SHAKE_CONFIRM_SAMPLES    2U
#define SHAKE_CLEAR_SAMPLES      5U

#define LOOP_DELAY_MS            100U
#define STATUS_REPORT_MS         1000U
#define LCD_REFRESH_MS           250U

#define LCD_RS                   (1U << 0)
#define LCD_RW                   (1U << 1)
#define LCD_EN                   (1U << 2)
#define LCD_BL                   (1U << 3)

typedef enum {
    SHAKE_LOW = 0,
    SHAKE_MEDIUM,
    SHAKE_INTENSE
} ShakeLevel_t;

typedef enum {
    REED_CLOSED = 0,
    REED_OPEN
} ReedState_t;

static volatile uint32_t g_ms_ticks = 0;
static uint32_t g_open_count = 0;
static uint8_t g_mpu_ok = 0;

void SysTick_Handler(void) {
    g_ms_ticks++;
}

static uint32_t millis(void) {
    return g_ms_ticks;
}

static void delay_ms(uint32_t ms) {
    uint32_t start = millis();
    while ((uint32_t)(millis() - start) < ms) {
        __NOP();
    }
}

/* ------------------------- Bluetooth / USART2 ------------------------- */

static void Bluetooth_Init(void) {
    RCC->AHB1ENR |= RCC_AHB1ENR_GPIOAEN;
    RCC->APB1ENR |= RCC_APB1ENR_USART2EN;

    GPIOA->MODER &= ~((3U << (2U * 2U)) | (3U << (3U * 2U)));
    GPIOA->MODER |=  ((2U << (2U * 2U)) | (2U << (3U * 2U)));
    GPIOA->AFR[0] &= ~((0xFU << (2U * 4U)) | (0xFU << (3U * 4U)));
    GPIOA->AFR[0] |=  ((7U << (2U * 4U)) | (7U << (3U * 4U)));
    GPIOA->OSPEEDR |= (2U << (2U * 2U)) | (2U << (3U * 2U));

    USART2->CR1 = 0U;
    USART2->CR2 = 0U;
    USART2->CR3 = 0U;
    USART2->BRR = 0x0683U; /* 9600 baud at 16 MHz PCLK1 */
    USART2->CR1 = USART_CR1_TE | USART_CR1_RE | USART_CR1_UE;
}

static void Bluetooth_SendChar(char c) {
    uint32_t timeout = 1000000UL;
    while ((USART2->SR & USART_SR_TXE) == 0U) {
        if (--timeout == 0U) return;
    }
    USART2->DR = (uint8_t)c;
}

static void Bluetooth_SendString(const char *s) {
    while (*s != '\0') Bluetooth_SendChar(*s++);
}

static void Bluetooth_SendEvent(const char *event, uint32_t count,
                                const char *shake) {
    char msg[112];
    (void)snprintf(msg, sizeof(msg),
                   "EVENT=%s,OPEN_COUNT=%lu,SHAKE=%s\r\n",
                   event, (unsigned long)count, shake);
    Bluetooth_SendString(msg);
}

/* ------------------------------ I2C1 ---------------------------------- */

static void I2C1_Init(void) {
    RCC->AHB1ENR |= RCC_AHB1ENR_GPIOBEN;
    RCC->APB1ENR |= RCC_APB1ENR_I2C1EN;

    GPIOB->MODER &= ~((3U << (6U * 2U)) | (3U << (7U * 2U)));
    GPIOB->MODER |=  ((2U << (6U * 2U)) | (2U << (7U * 2U)));
    GPIOB->OTYPER |= (1U << 6U) | (1U << 7U);
    GPIOB->OSPEEDR |= (3U << (6U * 2U)) | (3U << (7U * 2U));
    GPIOB->PUPDR &= ~((3U << (6U * 2U)) | (3U << (7U * 2U)));
    GPIOB->PUPDR |=  (1U << (6U * 2U)) | (1U << (7U * 2U));

    GPIOB->AFR[0] &= ~((0xFU << (6U * 4U)) | (0xFU << (7U * 4U)));
    GPIOB->AFR[0] |=  ((4U << (6U * 4U)) | (4U << (7U * 4U)));

    I2C1->CR1 |= I2C_CR1_SWRST;
    I2C1->CR1 &= ~I2C_CR1_SWRST;
    I2C1->CR2 = 16U;
    I2C1->CCR = 80U;
    I2C1->TRISE = 17U;
    I2C1->CR1 = I2C_CR1_PE;
}

static void I2C1_ResetBus(void) {
    I2C1->CR1 &= ~I2C_CR1_PE;
    for (volatile uint32_t i = 0; i < 100U; i++) __NOP();
    I2C1->CR1 |= I2C_CR1_PE;
}

static uint8_t I2C1_Start(void) {
    uint32_t timeout = I2C_TIMEOUT_CYCLES;
    I2C1->CR1 |= I2C_CR1_START;
    while ((I2C1->SR1 & I2C_SR1_SB) == 0U) {
        if (--timeout == 0U) {
            I2C1_ResetBus();
            return 1U;
        }
    }
    return 0U;
}

static void I2C1_Stop(void) {
    I2C1->CR1 |= I2C_CR1_STOP;
}

static uint8_t I2C1_SendAddress(uint8_t addr, uint8_t is_read) {
    uint32_t timeout = I2C_TIMEOUT_CYCLES;
    I2C1->DR = (uint8_t)((addr << 1U) | (is_read ? 1U : 0U));
    while ((I2C1->SR1 & I2C_SR1_ADDR) == 0U) {
        if (I2C1->SR1 & I2C_SR1_AF) {
            I2C1->SR1 &= ~I2C_SR1_AF;
            I2C1_Stop();
            return 1U;
        }
        if (--timeout == 0U) {
            I2C1_ResetBus();
            return 1U;
        }
    }
    (void)I2C1->SR1;
    (void)I2C1->SR2;
    return 0U;
}

static uint8_t I2C1_WriteByte(uint8_t data) {
    uint32_t timeout = I2C_TIMEOUT_CYCLES;
    while ((I2C1->SR1 & I2C_SR1_TXE) == 0U) {
        if (--timeout == 0U) {
            I2C1_ResetBus();
            return 1U;
        }
    }
    I2C1->DR = data;
    timeout = I2C_TIMEOUT_CYCLES;
    while ((I2C1->SR1 & I2C_SR1_BTF) == 0U) {
        if (--timeout == 0U) {
            I2C1_ResetBus();
            return 1U;
        }
    }
    return 0U;
}

static uint8_t I2C1_ReadAck(uint8_t *data) {
    uint32_t timeout = I2C_TIMEOUT_CYCLES;
    I2C1->CR1 |= I2C_CR1_ACK;
    while ((I2C1->SR1 & I2C_SR1_RXNE) == 0U) {
        if (--timeout == 0U) {
            I2C1_ResetBus();
            return 1U;
        }
    }
    *data = (uint8_t)I2C1->DR;
    return 0U;
}

static uint8_t I2C1_ReadNack(uint8_t *data) {
    uint32_t timeout = I2C_TIMEOUT_CYCLES;
    I2C1->CR1 &= ~I2C_CR1_ACK;
    while ((I2C1->SR1 & I2C_SR1_RXNE) == 0U) {
        if (--timeout == 0U) {
            I2C1_ResetBus();
            return 1U;
        }
    }
    *data = (uint8_t)I2C1->DR;
    I2C1_Stop();
    return 0U;
}

/* ------------------------------- LCD ---------------------------------- */

static void LCD_WriteIO(uint8_t value) {
    if (I2C1_Start() != 0U) return;
    if (I2C1_SendAddress(LCD_ADDR, 0U) != 0U) return;
    (void)I2C1_WriteByte(value);
    I2C1_Stop();
}

static void LCD_PulseEnable(uint8_t value) {
    LCD_WriteIO((uint8_t)(value | LCD_EN));
    for (volatile uint32_t i = 0; i < 120U; i++) __NOP();
    LCD_WriteIO((uint8_t)(value & (uint8_t)~LCD_EN));
    for (volatile uint32_t i = 0; i < 120U; i++) __NOP();
}

static void LCD_Send4Bits(uint8_t data, uint8_t rs) {
    uint8_t frame = (uint8_t)((data & 0xF0U) | LCD_BL | (rs ? LCD_RS : 0U));
    LCD_PulseEnable(frame);
}

static void LCD_SendCommand(uint8_t command) {
    LCD_Send4Bits(command & 0xF0U, 0U);
    LCD_Send4Bits((uint8_t)(command << 4U), 0U);
    if (command == 0x01U || command == 0x02U) delay_ms(2U);
}

static void LCD_SendData(uint8_t data) {
    LCD_Send4Bits(data & 0xF0U, 1U);
    LCD_Send4Bits((uint8_t)(data << 4U), 1U);
}

static void LCD_Init(void) {
    delay_ms(50U);
    LCD_Send4Bits(0x30U, 0U);
    delay_ms(5U);
    LCD_Send4Bits(0x30U, 0U);
    delay_ms(1U);
    LCD_Send4Bits(0x30U, 0U);
    delay_ms(1U);
    LCD_Send4Bits(0x20U, 0U);
    delay_ms(1U);
    LCD_SendCommand(0x28U);
    LCD_SendCommand(0x0CU);
    LCD_SendCommand(0x06U);
    LCD_SendCommand(0x01U);
}

static void LCD_SetCursor(uint8_t col, uint8_t row) {
    uint8_t offset = (row == 0U) ? 0x00U : 0x40U;
    LCD_SendCommand((uint8_t)(0x80U | (col + offset)));
}

static void LCD_PrintString(const char *str) {
    while (*str != '\0') LCD_SendData((uint8_t)*str++);
}

static void LCD_PrintLine(uint8_t row, const char *text) {
    uint8_t i;
    LCD_SetCursor(0U, row);
    for (i = 0U; i < 16U; i++) {
        char c = text[i];
        LCD_SendData((uint8_t)((c == '\0') ? ' ' : c));
        if (c == '\0') {
            i++;
            for (; i < 16U; i++) LCD_SendData(' ');
            break;
        }
    }
}

/* ----------------------------- MPU6050 -------------------------------- */

static uint8_t MPU6050_WriteRegister(uint8_t reg, uint8_t value) {
    if (I2C1_Start() != 0U) return 1U;
    if (I2C1_SendAddress(MPU6050_ADDR, 0U) != 0U) return 1U;
    if (I2C1_WriteByte(reg) != 0U) {
        I2C1_Stop();
        return 1U;
    }
    if (I2C1_WriteByte(value) != 0U) {
        I2C1_Stop();
        return 1U;
    }
    I2C1_Stop();
    return 0U;
}

static uint8_t MPU6050_Init(void) {
    /* Wake device and select +/-2g accelerometer range. */
    if (MPU6050_WriteRegister(0x6BU, 0x00U) != 0U) return 1U;
    delay_ms(100U);
    if (MPU6050_WriteRegister(0x1CU, 0x00U) != 0U) return 1U;
    return 0U;
}

static uint8_t MPU6050_ReadAccel(int16_t *ax, int16_t *ay, int16_t *az) {
    uint8_t buf[6];
    if (I2C1_Start() != 0U) return 1U;
    if (I2C1_SendAddress(MPU6050_ADDR, 0U) != 0U) return 1U;
    if (I2C1_WriteByte(0x3BU) != 0U) {
        I2C1_Stop();
        return 1U;
    }
    if (I2C1_Start() != 0U) return 1U;
    if (I2C1_SendAddress(MPU6050_ADDR, 1U) != 0U) return 1U;

    if (I2C1_ReadAck(&buf[0]) != 0U) return 1U;
    if (I2C1_ReadAck(&buf[1]) != 0U) return 1U;
    if (I2C1_ReadAck(&buf[2]) != 0U) return 1U;
    if (I2C1_ReadAck(&buf[3]) != 0U) return 1U;
    if (I2C1_ReadAck(&buf[4]) != 0U) return 1U;
    if (I2C1_ReadNack(&buf[5]) != 0U) return 1U;

    *ax = (int16_t)(((uint16_t)buf[0] << 8U) | buf[1]);
    *ay = (int16_t)(((uint16_t)buf[2] << 8U) | buf[3]);
    *az = (int16_t)(((uint16_t)buf[4] << 8U) | buf[5]);
    return 0U;
}

/* ---------------------------- Reed switch ----------------------------- */

static void ReedSwitch_Init(void) {
    RCC->AHB1ENR |= RCC_AHB1ENR_GPIOAEN;
    GPIOA->MODER &= ~(3U << 0U);
    GPIOA->PUPDR &= ~(3U << 0U);
    GPIOA->PUPDR |= (1U << 0U); /* Pull-up, matching reference code */
}

static ReedState_t ReedSwitch_ReadRaw(void) {
    uint8_t pin = (GPIOA->IDR & 1U) ? 1U : 0U;
    return (pin == REED_TAMPER_LOGIC) ? REED_OPEN : REED_CLOSED;
}

static const char *ShakeName(ShakeLevel_t level) {
    if (level == SHAKE_INTENSE) return "INTENSE";
    if (level == SHAKE_MEDIUM) return "MEDIUM";
    return "LOW";
}

/* ----------------------------- Application ---------------------------- */

int main(void) {
    int16_t ax = 0, ay = 0, az = 0;
    int16_t prev_ax = 0, prev_ay = 0, prev_az = 0;
    uint8_t have_previous_sample = 0U;

    ReedState_t reed_stable = REED_CLOSED;
    ReedState_t reed_candidate = REED_CLOSED;
    uint8_t reed_candidate_count = 0U;

    ShakeLevel_t shake_level = SHAKE_LOW;
    uint8_t shake_candidate_count = 0U;
    uint8_t shake_clear_count = 0U;

    uint32_t last_status_ms = 0U;
    uint32_t last_lcd_ms = 0U;
    uint32_t last_sensor_error_ms = 0U;
    uint8_t sensor_error_reported = 0U;

    char line1[17];
    char line2[17];
    char bt_line[128];

    /* Initialize millisecond tick using default 16 MHz system clock. */
    SysTick->LOAD = 16000U - 1U; /* 1 ms at 16 MHz */
    SysTick->VAL = 0U;
    SysTick->CTRL = SysTick_CTRL_CLKSOURCE_Msk |
                    SysTick_CTRL_TICKINT_Msk |
                    SysTick_CTRL_ENABLE_Msk;

    I2C1_Init();
    Bluetooth_Init();
    ReedSwitch_Init();
    LCD_Init();

    LCD_PrintLine(0U, "Parcel Monitor");
    LCD_PrintLine(1U, "Initializing...");
    Bluetooth_SendString("EVENT=STARTUP,STATUS=INITIALIZING\r\n");

    g_mpu_ok = (MPU6050_Init() == 0U) ? 1U : 0U;
    if (!g_mpu_ok) {
        LCD_PrintLine(0U, "MPU6050 ERROR");
        LCD_PrintLine(1U, "Check I2C/wiring");
        Bluetooth_SendString("ERROR=MPU6050_INIT_FAILED\r\n");
        /* Continue reed monitoring even if MPU6050 fails. Retry periodically. */
    } else {
        Bluetooth_SendString("EVENT=STARTUP,STATUS=READY\r\n");
    }

    /* Set initial stable reed state without counting a startup-open condition. */
    reed_stable = ReedSwitch_ReadRaw();
    reed_candidate = reed_stable;

    (void)snprintf(bt_line, sizeof(bt_line),
                   "STATUS=READY,REED=%s,OPEN_COUNT=%lu,SHAKE=LOW,MPU=%s\r\n",
                   (reed_stable == REED_OPEN) ? "OPEN" : "CLOSED",
                   (unsigned long)g_open_count, g_mpu_ok ? "OK" : "ERROR");
    Bluetooth_SendString(bt_line);

    while (1) {
        uint32_t now = millis();
        ReedState_t reed_raw = ReedSwitch_ReadRaw();
        uint8_t reed_open_event = 0U;
        uint8_t shake_changed = 0U;
        int32_t motion_score = 0;

        /* Debounce reed input. Count only a stable CLOSED -> OPEN transition. */
        if (reed_raw == reed_candidate) {
            if (reed_candidate_count < REED_DEBOUNCE_SAMPLES) {
                reed_candidate_count++;
            }
        } else {
            reed_candidate = reed_raw;
            reed_candidate_count = 1U;
        }

        if ((reed_candidate_count >= REED_DEBOUNCE_SAMPLES) &&
            (reed_stable != reed_candidate)) {
            reed_stable = reed_candidate;
            if (reed_stable == REED_OPEN) {
                g_open_count++;
                reed_open_event = 1U;
                Bluetooth_SendEvent("PARCEL_OPENED", g_open_count,
                                    ShakeName(shake_level));
            } else {
                Bluetooth_SendEvent("PARCEL_CLOSED", g_open_count,
                                    ShakeName(shake_level));
            }
        }

        /* Read accelerometer. Keep reed monitoring active if sensor read fails. */
        if (MPU6050_ReadAccel(&ax, &ay, &az) == 0U) {
            if (!g_mpu_ok) {
                g_mpu_ok = 1U;
                Bluetooth_SendString("STATUS=MPU6050_RECOVERED\r\n");
                sensor_error_reported = 0U;
            }

            if (have_previous_sample) {
                motion_score = abs((int32_t)ax - prev_ax) +
                               abs((int32_t)ay - prev_ay) +
                               abs((int32_t)az - prev_az);
            } else {
                have_previous_sample = 1U;
            }

            prev_ax = ax;
            prev_ay = ay;
            prev_az = az;

            /* Confirm higher levels over multiple samples; use hysteresis to clear. */
            if (motion_score >= SHAKE_INTENSE_THRESHOLD) {
                if (shake_level != SHAKE_INTENSE) {
                    if (shake_candidate_count < SHAKE_CONFIRM_SAMPLES) {
                        shake_candidate_count++;
                    }
                    if (shake_candidate_count >= SHAKE_CONFIRM_SAMPLES) {
                        shake_level = SHAKE_INTENSE;
                        shake_changed = 1U;
                        shake_clear_count = 0U;
                    }
                } else {
                    shake_candidate_count = 0U;
                }
            } else if (motion_score >= SHAKE_MEDIUM_THRESHOLD) {
                if (shake_level == SHAKE_LOW) {
                    if (shake_candidate_count < SHAKE_CONFIRM_SAMPLES) {
                        shake_candidate_count++;
                    }
                    if (shake_candidate_count >= SHAKE_CONFIRM_SAMPLES) {
                        shake_level = SHAKE_MEDIUM;
                        shake_changed = 1U;
                        shake_clear_count = 0U;
                    }
                } else if (shake_level == SHAKE_MEDIUM) {
                    shake_candidate_count = 0U;
                    shake_clear_count = 0U;
                } else {
                    /* Do not downgrade INTENSE immediately on one smaller sample. */
                    shake_candidate_count = 0U;
                    shake_clear_count = 0U;
                }
            } else if (motion_score < SHAKE_CLEAR_THRESHOLD) {
                shake_candidate_count = 0U;
                if (shake_clear_count < SHAKE_CLEAR_SAMPLES) {
                    shake_clear_count++;
                }
                if (shake_clear_count >= SHAKE_CLEAR_SAMPLES &&
                    shake_level != SHAKE_LOW) {
                    shake_level = SHAKE_LOW;
                    shake_changed = 1U;
                    shake_clear_count = 0U;
                }
            } else {
                shake_candidate_count = 0U;
                shake_clear_count = 0U;
            }

            /* If already INTENSE, allow a confirmed medium condition to downgrade. */
            if (shake_level == SHAKE_INTENSE &&
                motion_score >= SHAKE_MEDIUM_THRESHOLD &&
                motion_score < SHAKE_INTENSE_THRESHOLD) {
                /* Keep INTENSE until the clear threshold is sustained. */
            }

            if (shake_changed) {
                (void)snprintf(bt_line, sizeof(bt_line),
                               "EVENT=SHAKE_LEVEL_CHANGED,SHAKE=%s,OPEN_COUNT=%lu\r\n",
                               ShakeName(shake_level), (unsigned long)g_open_count);
                Bluetooth_SendString(bt_line);
                shake_changed = 0U;
            }
        } else {
            I2C1_ResetBus();
            g_mpu_ok = 0U;
            have_previous_sample = 0U;
            if (!sensor_error_reported ||
                (uint32_t)(now - last_sensor_error_ms) >= 5000U) {
                Bluetooth_SendString("ERROR=MPU6050_READ_FAILED\r\n");
                last_sensor_error_ms = now;
                sensor_error_reported = 1U;
            }
        }

        /* LCD refresh at a modest rate to avoid needless I2C traffic. */
        if ((uint32_t)(now - last_lcd_ms) >= LCD_REFRESH_MS) {
            last_lcd_ms = now;

            if (!g_mpu_ok) {
                LCD_PrintLine(0U, "MPU6050 ERROR");
                (void)snprintf(line2, sizeof(line2), "Open:%lu",
                               (unsigned long)g_open_count);
                LCD_PrintLine(1U, line2);
            } else if (reed_stable == REED_OPEN) {
                LCD_PrintLine(0U, "PARCEL WAS OPENED");
                (void)snprintf(line2, sizeof(line2), "Open Count:%lu",
                               (unsigned long)g_open_count);
                LCD_PrintLine(1U, line2);
            } else {
                (void)snprintf(line1, sizeof(line1), "Parcel: SECURE");
                (void)snprintf(line2, sizeof(line2), "Shake: %-7s",
                               ShakeName(shake_level));
                LCD_PrintLine(0U, line1);
                LCD_PrintLine(1U, line2);
            }
        }

        /* Periodic heartbeat/status for the future laptop dashboard. */
        if ((uint32_t)(now - last_status_ms) >= STATUS_REPORT_MS) {
            last_status_ms = now;
            (void)snprintf(bt_line, sizeof(bt_line),
                           "STATUS=%s,REED=%s,OPEN_COUNT=%lu,SHAKE=%s,"
                           "AX=%d,AY=%d,AZ=%d,MPU=%s\r\n",
                           (reed_stable == REED_OPEN) ? "OPENED" : "SECURE",
                           (reed_stable == REED_OPEN) ? "OPEN" : "CLOSED",
                           (unsigned long)g_open_count, ShakeName(shake_level),
                           (int)ax, (int)ay, (int)az, g_mpu_ok ? "OK" : "ERROR");
            Bluetooth_SendString(bt_line);
        }

        (void)reed_open_event;
        delay_ms(LOOP_DELAY_MS);
    }
}
