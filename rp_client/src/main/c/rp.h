/*
 * rp.h
 *
 *  Created on: 2016年12月16日
 *      Author: zhkl0228
 */

#ifndef RP_H_
#define RP_H_

#include <pthread.h>
#include <sys/select.h>
#include <stdbool.h>

#define RP_VERSION 0x50010

typedef struct {
	char hostname[64];
	char port[16];
	char path[128];
	char query[256];
} Url;

typedef struct write_buffer {
	char *buf;
	size_t size;
	size_t index;
	struct write_buffer *next;
} write_buffer;

typedef struct {
	char *buf;
	size_t pos;
	size_t limit;
	size_t cap;
} read_buffer;

enum login_status { offline, request_login, logged };

typedef struct {
	void (*on_ready)(void *arg, long long timeMillis);
	void (*on_data)(void *arg, long long timeMillis);
	void (*on_ready_error)(void *arg, char *exception);
	void (*on_data_error)(void *arg, char *exception);
} select_callback;

typedef struct socket_proxy {
	int socket;
	char host[32];
	char port[8];
	int fd;
	int timeout;
	write_buffer *buffer;

	bool notify_connect;
	bool connected;
	bool request_close;
	bool connect_timeout;

	long long connect_time;
	long long last_touch;
	struct socket_proxy **prev;
	struct socket_proxy *next;

	select_callback callback;

	void *rp;

	char shutdown;
} socket_proxy;

typedef struct {
	pthread_t thread;

	char address[32];
	char port[8];
	char username[32];
	char password[64];
	char extra[128];

	int fd;

	long long last_check_session;
	int network_delay;

	socket_proxy *sockets;

	write_buffer *buffer;
	read_buffer *read;

	enum login_status status;
	bool can_stop;
	bool connected;
	bool reconnect;
	long long connect_time;
	long long last_touch;

	select_callback callback;
} rp;

bool rp_running(rp *rp);

rp* start_rp(char *address, char *port, char *username, char *password, char *extra);

void stop_rp(rp *rp);

/**
 * channel initializer
 */
void rp_loop(char *channel_id);

#endif /* RP_H_ */
