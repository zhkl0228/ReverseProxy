/*
 * md5.h
 *
 *  Created on: 2016年12月18日
 *      Author: zhkl0228
 */

#ifndef MD5_H_
#define MD5_H_

#include <stdint.h>

void md5(const uint8_t *initial_msg, size_t initial_len, uint8_t *digest);

#endif /* MD5_H_ */
