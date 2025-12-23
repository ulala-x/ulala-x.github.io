#ifndef MOCK_ZMQ_H
#define MOCK_ZMQ_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Send simulation - reads data and returns checksum
 * Simulates sending data to a network socket
 *
 * @param data Pointer to data buffer
 * @param len Length of data in bytes
 * @return Checksum of the data
 */
int64_t mock_send(const uint8_t* data, size_t len);

/**
 * Receive simulation - writes pattern data to buffer
 * Simulates receiving data from a network socket
 *
 * @param buf Pointer to destination buffer
 * @param len Length of buffer in bytes
 * @return Number of bytes written
 */
int64_t mock_recv(uint8_t* buf, size_t len);

/**
 * Transform simulation - in-place XOR operation
 * Simulates data transformation (e.g., encryption/decryption)
 *
 * @param data Pointer to data buffer (modified in-place)
 * @param len Length of data in bytes
 */
void mock_transform(uint8_t* data, size_t len);

#ifdef __cplusplus
}
#endif

#endif // MOCK_ZMQ_H
