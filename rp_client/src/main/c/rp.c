/*
 * rp.c
 *
 *  Created on: 2016年12月16日
 *      Author: zhkl0228
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <arpa/inet.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <sys/time.h>
#include <errno.h>
#include <fcntl.h>
#include <time.h>
#include <unistd.h>
#include <sys/utsname.h>

#include "dbg.h"
#include "md5.h"
#include "xml.h"

#include <buffer.h>
#include "connect.h"

#include "rp.h"

#define RP_parseConnect 0x1
#define RP_parseWriteData 0x2
#define RP_parseClose 0x3
#define RP_messageReceived 0x4
#define RP_sendException 0x6
#define RP_requestAuth 0x7
#define RP_checkSession 0x8
#define RP_shutdownHalf 0xc

#define READ_BUFFER_SIZE 1024

typedef struct {
	char *buf;
	int size;
	int read;
} packet;

static size_t write_short(char *data, size_t val) {
	data[0] = (val >> 8) & 0xff;
	data[1] = val & 0xff;
	return 2;
}

static size_t write_int(char *data, size_t val) {
	data[0] = (val >> 24) & 0xff;
	data[1] = (val >> 16) & 0xff;
	data[2] = (val >> 8) & 0xff;
	data[3] = val & 0xff;
	return 4;
}

static size_t write_bytes(char *data, void *mem, size_t size) {
	memcpy(data, mem, size);
	return size;
}

static size_t write_utf(char *data, char *str) {
	size_t len = strlen(str);
	size_t off = write_short(data, len);
	// debug("write_utf: len=%lu, off=%lu, str=%s", len, off, str);
	memcpy(&data[off], str, len);
	return len + off;
}

static size_t write_long(char *data, long long val) {
	data[0] = (char) ((val >> 56) & 0xff);
	data[1] = (char) ((val >> 48) & 0xff);
	data[2] = (char) ((val >> 40) & 0xff);
	data[3] = (char) ((val >> 32) & 0xff);
	data[4] = (char) ((val >> 24) & 0xff);
	data[5] = (char) ((val >> 16) & 0xff);
	data[6] = (char) ((val >>  8) & 0xff);
	data[7] = (char) (val & 0xff);
	return 8;
}

static int read_int(char *data, size_t index) {
	int b1 = data[index] & 0xff;
	int b2 = data[index + 1] & 0xff;
	int b3 = data[index + 2] & 0xff;
	int b4 = data[index + 3] & 0xff;
	return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
}

static short read_short(char *data, size_t index) {
	short b1 = data[index] & 0xff;
	short b2 = data[index + 1] & 0xff;
	return (b1 << 8) | b2;
}

static size_t read_utf(char *data, size_t index, char *out) {
	short len = read_short(data, index);
	for(int i = 0; i < len; i++) {
		out[i] = data[index + 2 + i];
	}
	out[len] = '\0';
	return len + 2;
}

static long long read_long(char *data, size_t index) {
	long long b1 = data[index] & 0xff;
	long long b2 = data[index + 1] & 0xff;
	long long b3 = data[index + 2] & 0xff;
	long long b4 = data[index + 3] & 0xff;
	long long b5 = data[index + 4] & 0xff;
	long long b6 = data[index + 5] & 0xff;
	long long b7 = data[index + 6] & 0xff;
	long long b8 = data[index + 7] & 0xff;
	return (b1 << 56) | (b2 << 48) | (b3 << 40) | (b4 << 32) | (b5 << 24) | (b6 << 16) | (b7 << 8) | b8;
}

static long long currentTimeMillis() {
	struct timeval tv;
	gettimeofday(&tv, NULL);
	long long t = tv.tv_sec;
	t *= 1000;
	t += tv.tv_usec / 1000;
	return  t;
}

static void write_buffer_destroy(write_buffer **buffer) {
	if(*buffer == NULL) {
		return;
	}

	write_buffer *next = *buffer;
	while(next != NULL) {
		*buffer = next->next;
		free(next->buf);
		free(next);
		next = *buffer;
	}
}

/**
 * return next proxy
 */
static socket_proxy *destroy_socket_proxy(socket_proxy *proxy) {
	// debug("destroy_socket_proxy proxy=%p", proxy);

	write_buffer_destroy(&proxy->buffer);

	if(proxy->next) {
		proxy->next->prev = proxy->prev;
	}
	*proxy->prev = proxy->next;

	free(proxy);

	return *proxy->prev;
}

static void close_all_socket_proxy(rp *rp) {
	debug("close_all_socket_proxy rp=%p", rp);
	while(rp->sockets) {
		destroy_socket_proxy(rp->sockets);
	}
	debug("close_all_socket_proxy done.");
}

static void close_rp(rp *rp, char *exception) {
	close_all_socket_proxy(rp);
	if(rp->fd > 0) {
		close(rp->fd);
		debug("socket closed: %s:%s, exception=%s", rp->address, rp->port, exception);
	} else {
		log_err("close_client fd is NULL.\n");
	}

	rp->fd = 0;
	rp->status = offline;
	rp->connected = false;
	write_buffer_destroy(&rp->buffer);
}

static void write_buffer_append(char *buf, size_t size, write_buffer **buffer) {
	size_t size_of_buffer = sizeof(write_buffer);
	write_buffer *new = malloc(size_of_buffer);
	memset(new, 0, size_of_buffer);
	new->buf = malloc(size);
	memcpy(new->buf, buf, size);
	new->size = size;
	new->index = 0;
	new->next = NULL;

	if(*buffer == NULL) {
		*buffer = new;
		return;
	}

	write_buffer *next = *buffer;
	while(next->next != NULL) {
		next = next->next;
	}
	next->next = new;
}

static void send_response(char *buf, size_t size, write_buffer **buffer) {
	write_int(buf, size - 4);
	write_buffer_append(buf, size, buffer);
}

static void send_exception(rp *rp, int socket, char *msg) {
	char buf[512];
	size_t index = 4;

	buf[index++] = RP_sendException;
	index += write_int(&buf[index], socket);
	index += write_utf(&buf[index], msg);
	send_response(buf, index, &rp->buffer);
}

static void add_socket_proxy(rp *rp, socket_proxy *proxy) {
	// debug("add_socket_proxy fd=0x%x, proxy=%p", proxy->fd, proxy);

	if(rp->sockets == NULL) {
		proxy->prev = &rp->sockets;
		rp->sockets = proxy;
		return;
	}

	socket_proxy *next = rp->sockets;
	while(next->next != NULL) {
		next = next->next;
	}

	proxy->prev = &next->next;
	next->next = proxy;
}

static socket_proxy *find_by_socket(rp *rp, int socket) {
	// debug("find_by_socket socket=0x%x", socket);

	socket_proxy *ret = NULL;
	socket_proxy *next = rp->sockets;
	while(next != NULL) {
		if(next->socket == socket) {
			ret = next;
			break;
		}
		next = next->next;
	}

	return ret;
}

/**
Sets a socket to non blocking state.
*/
static int sock_set_non_block(int fd) // Thanks to Bjorn Reese
{
/* If they have O_NONBLOCK, use the Posix way to do it */
#if defined(O_NONBLOCK)
  int flags;
  if (-1 == (flags = fcntl(fd, F_GETFL, 0)))
    flags = 0;
  // debug("flags initial value was %d", flags);
  return fcntl(fd, F_SETFL, flags | O_NONBLOCK);
#else
  /* Otherwise, use the old way of doing it */
  static int flags = 1;
  return ioctl(fd, FIOBIO, &flags);
#endif
}

static int sock_connect(char *address, char *port) {
	errno = 0; // clean errorno

	  // setup the address
	  struct addrinfo hints;
	  struct addrinfo *addrinfo = NULL;       // will point to the results
	  int fd;
	  memset(&hints, 0, sizeof(struct addrinfo)); // make sure the struct is empty
	  // debug("sock_connect address=%s, port=%s, ap=%p, pp=%p, hints=%p, addrinfo=%p", address, port, address, port, &hints, &addrinfo);
	  hints.ai_family = AF_INET;     // don't care IPv4 or IPv6
	  hints.ai_socktype = SOCK_STREAM; // TCP stream sockets
	  // hints.ai_flags = AI_PASSIVE;     // fill in my IP for me
	  if (getaddrinfo(address, port, &hints, &addrinfo)) {
	    return -1;
	  }
	  // debug("review_lib address=%s, port=%s", address, port);
	  // get the file descriptor
	  fd = socket(addrinfo->ai_family, addrinfo->ai_socktype, addrinfo->ai_protocol);
	  // debug("sock_connect bug address=%s, port=%s", address, port);
	  if (fd <= 0) {
	    freeaddrinfo(addrinfo);
	    return -1;
	  }
	  // make sure the socket is non-blocking
	  if (sock_set_non_block(fd) < 0) {
	    freeaddrinfo(addrinfo);
	    close(fd);
	    return -1;
	  }

	  if (connect(fd, addrinfo->ai_addr, addrinfo->ai_addrlen) < 0 &&
	      errno != EINPROGRESS) {
	    close(fd);
	    freeaddrinfo(addrinfo);
	    return -1;
	  }
	  freeaddrinfo(addrinfo);
	  return fd;
}

static void sock_write_buffer(int fd, write_buffer **buffer) {
	if(buffer && *buffer) {
		write_buffer *next = *buffer;
		while(next != NULL) {
			size_t off = next->size - next->index;
			if(off > 0) {
				long write_size = write(fd, next->buf, off);
				if(write_size <= 0) {
					if(errno == EWOULDBLOCK || errno == EAGAIN) { // write buffer full
						break;
					} else { // write failed.
						log_err("sock_write_buffer write failed: fd=%d", fd);
						break;
					}
				}

				next->index += write_size;

				if(write_size < off) { // write buffer full
					break;
				}
			}

			*buffer = next->next;
			free(next->buf);
			free(next);
			next = *buffer;
		}
	}
}

static void proxy_on_ready_error(void *arg, char *exception) {
	debug("proxy_on_ready_error arg=%p, exception=%s", arg, exception);

	socket_proxy *proxy = arg;
	rp *rp = proxy->rp;

	proxy->request_close = true;
	send_exception(rp, proxy->socket, exception);
}

static void proxy_on_data_error(void *arg, char *exception) {
	debug("proxy_on_data_error arg=%p, exception=%s", arg, exception);

	socket_proxy *proxy = arg;
	rp *rp = proxy->rp;

	proxy->request_close = true;

	char buf[16];
	size_t index = 4;
	buf[index++] = RP_parseClose;
	index += write_int(&buf[index], proxy->socket);
	send_response(buf, index, &rp->buffer);
}

static void proxy_on_ready(void *arg, long long timeMillis) {
	socket_proxy *proxy = arg;
	rp *rp = proxy->rp;

	proxy->connected = true;
	proxy->last_touch = timeMillis;

	if(!proxy->notify_connect) {
		char buf[256];
		size_t index = 4;
		buf[index++] = RP_parseConnect;
		index += write_int(&buf[index], proxy->socket);

		struct sockaddr_in sin;
		socklen_t size = sizeof(sin);
		getsockname(proxy->fd, (struct sockaddr *) &sin, &size);
		// char *ip = inet_ntoa(sin.sin_addr);
		int port = sin.sin_port & 0xffff;
		// debug("reactor_on_ready ip=%s, port=%d", ip, port);

		buf[index++] = 4;//ipv4
		index += write_bytes(&buf[index], &sin.sin_addr, 4);
		index += write_short(&buf[index], port);

		send_response(buf, index, &rp->buffer);
		proxy->notify_connect = true;
		// debug(" proxy_on_ready proxy=%p connect=%s:%s fd=%d", proxy, proxy->host, proxy->port, proxy->fd);
	}

	sock_write_buffer(proxy->fd, &proxy->buffer);
}

static void proxy_on_data(void *arg, long long timeMillis) {
	socket_proxy *proxy = arg;
	rp *rp = proxy->rp;

	char buf[READ_BUFFER_SIZE];
	long count;
	int total = 0;
	while((count = read(proxy->fd, buf, READ_BUFFER_SIZE)) > 0) {
		total += count;

		// debug("proxy_on_data: proxy=%p, read=%d, fd=%d", proxy, read, fd);
		proxy->last_touch = timeMillis;

		char out[1024 + 16];
		size_t index = 4;
		out[index++] = RP_messageReceived;
		index += write_int(&out[index], proxy->socket);
		index += write_int(&out[index], count);
		index += write_bytes(&out[index], buf, count);
		send_response(out, index, &rp->buffer);

		if(count < READ_BUFFER_SIZE) {
			break;
		}
	}

	if(errno == EAGAIN || errno == EWOULDBLOCK) {
		return;
	}

	if(total < 1) { // EOF
		proxy_on_data_error(arg, "EOF");
	}
}

static void process_packet(rp *rp, packet *pp) {
	char auth_status;
	char auth_msg[256];
	memset(auth_msg, 0, 256);
	long long expire = 0;
	char auth_nick[256];
	memset(auth_nick, 0, 256);

	long long timeMillis, lastMillis;

	pp->read = 0;
	char type = pp->buf[pp->read++];

	// debug("process_packet type=%d, size=%d, read=%d, fd=%d", type, pp->size, pp->read, rp->fd);

	int socket;
	char host[128];
	memset(host, 0, 128);
	int port_value;
	char port[8];
	memset(port, 0, 8);
	int timeout;
	int keep_alive, oob_inline, has_ip;
	int receive_buffer_size, send_buffer_size;
	struct in_addr addr;
	char ip[16];
	memset(ip, 0, 16);
	int connect_fd;

	int length;
	socket_proxy *proxy;

	char shutdown_input;

	switch(type) {
	case RP_parseConnect:
		socket = read_int(pp->buf, pp->read);
		pp->read += 4;

		pp->read += read_utf(pp->buf, pp->read, host);

		port_value = read_short(pp->buf, pp->read) & 0xffff;
		sprintf(port, "%d", port_value);
		pp->read += 2;

		timeout = read_int(pp->buf, pp->read);
		pp->read += 4;

		keep_alive = pp->buf[pp->read++];
		oob_inline = pp->buf[pp->read++];

		receive_buffer_size = read_int(pp->buf, pp->read);
		pp->read += 4;

		pp->read++;//reuse_address

		send_buffer_size = read_int(pp->buf, pp->read);
		pp->read += 4;

		pp->read++;//tcp_no_delay
		pp->read++;//traffic_class

		has_ip = 0;
		if(pp->read < pp->size && pp->buf[pp->read++] == 4) {
			memcpy(&addr, &pp->buf[pp->read], 4);
			const char *ps = inet_ntoa(addr);
			strcpy(ip, ps);
			has_ip = 1;
			pp->read += 4;
		}

		size_t size_of_proxy = sizeof(socket_proxy);
		proxy = malloc(size_of_proxy);
		// debug("RP_parseConnect host=%s, port=%s, ip=%s, proxy=%p, socket=%p, notify_connect=%p, host=%p, port=%p, fd=%p, next=%p", host, port, ip, proxy, &proxy->socket, &proxy->notify_connect, &proxy->host, &proxy->port, &proxy->fd, &proxy->next);
		memset(proxy, 0, size_of_proxy);
		proxy->socket = socket;
		if(has_ip) {
			strcpy(proxy->host, ip);
		} else {
			strcpy(proxy->host, host);
		}
		strcpy(proxy->port, port);
		proxy->shutdown = 0;
		proxy->next = NULL;

		// debug("RP_parseConnect size=%d, read=%d, has_ip=%d, host=%s, port=%s", pp->size, pp->read, has_ip, proxy->host, proxy->port);
		// debug("RP_parseConnect proxy=%p connect=%s:%s ip=%d", proxy, proxy->host, proxy->port, has_ip);

		connect_fd = sock_connect(proxy->host, proxy->port);
		proxy->connect_time = currentTimeMillis();
		proxy->connect_timeout = false;

		// debug("RP_parseConnect bug size=%d, read=%d, connect_fd=%d", pp->size, pp->read, connect_fd);
		if(connect_fd == -1) {
			free(proxy);
			char msg[256];
			memset(msg, 0, 256);
			sprintf(msg, "sock_connect failed %s:%s", host, port);
			send_exception(rp, socket, msg);
		} else {
			setsockopt(connect_fd, SOL_SOCKET, SO_KEEPALIVE, &keep_alive, sizeof(keep_alive));
			setsockopt(connect_fd, SOL_SOCKET, SO_OOBINLINE, &oob_inline, sizeof(oob_inline));
			setsockopt(connect_fd, SOL_SOCKET, SO_RCVBUF, &receive_buffer_size, sizeof(receive_buffer_size));
			setsockopt(connect_fd, SOL_SOCKET, SO_SNDBUF, &send_buffer_size, sizeof(send_buffer_size));

			proxy->timeout = timeout;
			proxy->fd = connect_fd;
			proxy->rp = rp;
			select_callback *callback = &proxy->callback;
			callback->on_ready = proxy_on_ready;
			callback->on_data = proxy_on_data;
			callback->on_ready_error = proxy_on_ready_error;
			callback->on_data_error = proxy_on_data_error;
			add_socket_proxy(rp, proxy);
		}
		break;
	case RP_parseWriteData:
		socket = read_int(pp->buf, pp->read);
		pp->read += 4;
		length = read_int(pp->buf, pp->read);
		pp->read += 4;
		proxy = find_by_socket(rp, socket);
		// debug("RP_parseWriteData proxy=%p", proxy);
		if(proxy != NULL) {
			write_buffer_append(&pp->buf[pp->read], length, &proxy->buffer);
		} else {
			// debug("RP_parseWriteData find_by_socket failed: rp=%p, socket=0x%x", rp, socket);
			char buf[16];
			size_t index = 4;
			buf[index++] = RP_parseClose;
			index += write_int(&buf[index], socket);
			send_response(buf, index, &rp->buffer);
		}
		break;
	case RP_parseClose:
		socket = read_int(pp->buf, pp->read);
		pp->read += 4;
		proxy = find_by_socket(rp, socket);
		if(proxy) {
			proxy->request_close = true;
		} else {
			// debug("RP_parseClose find_by_socket failed: rp=%p, socket=0x%x", rp, socket);
		}
		break;
	case RP_requestAuth:
		auth_status = pp->buf[pp->read++];
		if(pp->buf[pp->read++] == 1) { // msg
			pp->read += read_utf(pp->buf, pp->read, auth_msg);
		}
		if(pp->buf[pp->read++] == 1) {
			expire = read_long(pp->buf, pp->read);
			pp->read += 8;
		}
		if(pp->buf[pp->read++] == 1) { // nick
			pp->read += read_utf(pp->buf, pp->read, auth_nick);
		}
		if(pp->read < pp->size) {
			rp->reconnect = pp->buf[pp->read++];
		}
		if(pp->size - pp->read >= 8) {
			timeMillis = currentTimeMillis();
			lastMillis = read_long(pp->buf, pp->read);
			pp->read += 8;
			rp->network_delay = (int) (timeMillis - lastMillis);
		}
		debug("RP_requestAuth msg=%s, expire=%lld, nick=%s, reconnect=%d, network_delay=%d", auth_msg, expire, auth_nick, rp->reconnect, rp->network_delay);
		if(auth_status == 0) { // log success
			rp->status = logged;
		} else {
			close_rp(rp, "auth failed.");
		}
		break;
	case RP_checkSession:
		timeMillis = currentTimeMillis();
		lastMillis = read_long(pp->buf, pp->read);
		pp->read += 8;
		rp->network_delay = (int) (timeMillis - lastMillis);
		// debug("set network_delay: %d, timeMillis=%ld, lastMillis=%ld, index=%d", client->network_delay, timeMillis, lastMillis, pp->read);
		break;
	case RP_shutdownHalf:
		socket = read_int(pp->buf, pp->read);
		pp->read += 4;
		shutdown_input = pp->buf[pp->read++];
		proxy = find_by_socket(rp, socket);
		if(proxy) {
			if(shutdown_input) {
				proxy->shutdown |= 1;
			} else {
				proxy->shutdown |= 2;
			}
			debug("RP_shutdownHalf: rp=%p, socket=0x%x, shutdown=0x%x", rp, socket, proxy->shutdown);
		} else {
			debug("RP_shutdownHalf find_by_socket failed: rp=%p, socket=0x%x", rp, socket);
		}
		break;
	default:
		debug("process_packet type=0x%x", type);
		break;
	}
}

static void rp_on_error(void *arg, char *exception) {
	rp *rp = arg;

	debug("rp_on_error arg=%p, exception=%s", arg, exception);

	close_rp(rp, exception);
}

static void rp_on_data(void *arg, long long timeMillis) {
	rp *rp = arg;
	packet pp;

	char buf[READ_BUFFER_SIZE];
	long count;
	int total = 0;
	while((count = read(rp->fd, buf, READ_BUFFER_SIZE)) > 0) {
		total += count;

		rp->last_touch = timeMillis;

		read_buffer *rb;
		if(rp->read == NULL) {
			size_t size_of_read = sizeof(read_buffer);
			rb = malloc(size_of_read);
			memset(rb, 0, size_of_read);
			rb->limit = (count + 7) / 8 * 8;
			rb->cap = rb->limit;
			rb->buf = malloc(rb->cap);
			rb->pos = 0;
			rp->read = rb;
			// debug("rp_on_data init: cap=%zu", rb->cap);
		} else {
			rb = rp->read;
		}

		if(rb->limit - rb->pos < count) {
			rb->limit = (rb->cap + rb->limit - rb->pos + count + 7) / 8 * 8;
			rb->cap = rb->limit;
			char *bb = malloc(rb->cap);
			if(rb->pos > 0) {
				memcpy(bb, rb->buf, rb->pos);
			}
			free(rb->buf);
			rb->buf = bb;
		}

		memcpy(&rb->buf[rb->pos], buf, count);
		rb->pos += count;

		// debug("rp_on_data buffer: cap=%zu, pos=%zu", rb->cap, rb->pos);

		rb->limit = rb->pos; rb->pos = 0; // flip

		while(1) {
			size_t remaining = rb->limit - rb->pos;
			if(remaining < 4) {
				break;
			}

			pp.size = read_int(rb->buf, rb->pos);
			if(remaining - 4 < pp.size) {
				break;
			}

			rb->pos += 4;
			pp.buf = &rb->buf[rb->pos];
			rb->pos += pp.size;
			pp.read = 0;

			// debug("rp_on_data size=%d, pp=%p, sp=%p, size=%d, buf=%p, fd=%d", pp.size, &pp, &pp.size, pp.size, pp.buf, fd);
			process_packet(rp, &pp);
		}

		if(rb->pos > 0) {//compact
			size_t remaining = rb->limit - rb->pos;
			memcpy(rb->buf, &rb->buf[rb->pos], remaining);
		}
		rb->pos = rb->limit - rb->pos;
		rb->limit = rb->cap;

		if(count < READ_BUFFER_SIZE) {
			break;
		}
	}

	if(errno == EAGAIN || errno == EWOULDBLOCK) {
		return;
	}

	if(total < 1) { // EOF
		rp_on_error(arg, "EOF");
	}
}

static void rp_on_ready(void *arg, long long timeMillis) {
	// debug("rp_on_ready arg=%p", arg);

	rp *rp = arg;

	rp->connected = true;
	rp->last_touch = timeMillis;

	write_buffer **buffer = &rp->buffer;
	if(rp->status == offline) {
		char buf[256];
		size_t index = 4;
		buf[index++] = RP_requestAuth;
		index += write_utf(&buf[index], rp->username);
		index += write_utf(&buf[index], rp->password);
		index += write_int(&buf[index], RP_VERSION);
		if(rp->extra[0]) {
			buf[index++] = 1;
			index += write_utf(&buf[index], rp->extra);
		} else {
			buf[index++] = 0;//no extra data
		}
		buf[index++] = 0;//can not change ip
		index += write_long(&buf[index], timeMillis);
		buf[index++] = 0;//no device info

		send_response(buf, index, buffer);
		rp->status = request_login;
	}

	sock_write_buffer(rp->fd, buffer);
}

static void check_session(rp *rp, long long timeMillis) {
	char buf[24];
	size_t index = 4;
	buf[index++] = RP_checkSession;
	index += write_int(&buf[index], rp->network_delay);
	index += write_long(&buf[index], timeMillis);
	index += write_int(&buf[index], 0); // last network status code

	send_response(buf, index, &rp->buffer);
}

static void process_on_ready(void *arg, select_callback *callback, int fd, fd_set *writefds, long long timeMillis) {
	int err = 0;
	socklen_t errlen = sizeof(err);

	if (getsockopt(fd, SOL_SOCKET, SO_ERROR, &err, &errlen) == -1) {
		char exception[256];
		memset(exception, 0, 256);
		sprintf(exception, "on_ready_error getsockopt(SO_ERROR) failed: errno=%d, %s", errno, clean_errno());
		callback->on_ready_error(arg, exception);
		return;
	}

	if(err) {
		char exception[256];
		memset(exception, 0, 256);
		sprintf(exception, "on_ready_error failed: err=%d, %s", err, strerror(err));
		callback->on_ready_error(arg, exception);
		return;
	}

	callback->on_ready(arg, timeMillis);
	FD_CLR(fd, writefds);
}

static void process_on_data(void *arg, select_callback *callback, int fd, fd_set *readfds, long long timeMillis) {
	int err = 0;
	socklen_t errlen = sizeof(err);

	if (getsockopt(fd, SOL_SOCKET, SO_ERROR, &err, &errlen) == -1) {
		char exception[256];
		memset(exception, 0, 256);
		sprintf(exception, "on_data_error getsockopt(SO_ERROR) failed: errno=%d, %s", errno, clean_errno());
		callback->on_data_error(arg, exception);
		return;
	}

	if(err) {
		char exception[256];
		memset(exception, 0, 256);
		sprintf(exception, "on_data_error read failed: err=%d, %s", err, strerror(err));
		callback->on_data_error(arg, exception);
		return;
	}

	callback->on_data(arg, timeMillis);
	FD_CLR(fd, readfds);
}

static void reactor_review(rp *rp, long long timeMillis) {
	fd_set readfds;
	fd_set writefds;

	int maxfd = 0;

	FD_ZERO(&readfds);
	FD_ZERO(&writefds);

	if(rp->connected) {
		FD_SET(rp->fd, &readfds);

		maxfd = rp->fd;
	}
	if(rp->buffer != NULL || !rp->connected) {
		FD_SET(rp->fd, &writefds);

		// debug("reactor_review fd=%d, buffer=%p, status=%d", rp->fd, rp->buffer, rp->status);

		maxfd = rp->fd;
	}

	socket_proxy *next = rp->sockets;
	while(next != NULL) {
		if(next->connected) {
			FD_SET(next->fd, &readfds);

			if(next->fd > maxfd) {
				maxfd = next->fd;
			}
		}
		if(next->buffer != NULL || !next->connected) {
			if(!next->connected && next->timeout > 0 && timeMillis - next->connect_time >= next->timeout) { // proxy connect timeout
				next->connect_time = timeMillis;
				next->connect_timeout = true;
				debug("detected connect timeout rp=%p: %s:%s, fd=%d, timeout=%d", next, next->host, next->port, next->fd, next->timeout);
			} else {
				FD_SET(next->fd, &writefds);

				// debug("reactor_review fd=%d, buffer=%p, notify_connect=%d", next->fd, next->buffer, next->notify_connect);

				if(next->fd > maxfd) {
					maxfd = next->fd;
				}
			}
		}
		next = next->next;
	}

	void *args[maxfd + 1];
	select_callback *callback[maxfd + 1];
	if(rp->fd <= maxfd) {
		args[rp->fd] = rp;
		callback[rp->fd] = &rp->callback;
	}

	next = rp->sockets;
	while(next != NULL) {
		if(next->fd <= maxfd) {
			args[next->fd] = next;
			callback[next->fd] = &next->callback;
		}
		next = next->next;
	}

	struct timeval timeout;
	timeout.tv_sec = 1;
	timeout.tv_usec = 0;

	int ret = select(maxfd + 1, &readfds, &writefds, NULL, &timeout);
	// debug("reactor_review select maxfd=%d, ret=%d", maxfd, ret);
	if(ret == -1) {
		// log_err("select failed.");
		close_rp(rp, "select failed.");
		return;
	}

	if(ret == 0) {
		return;
	}

	for(int i = 0; i <= maxfd; i++) {
		errno = 0;

		if(i == rp->fd && rp->connected && FD_ISSET(i, &readfds)) { // connected rp read first
			process_on_data(args[i], callback[i], i, &readfds, timeMillis);
		}

		if(FD_ISSET(i, &writefds)) { // proxy or rp write
			process_on_ready(args[i], callback[i], i, &writefds, timeMillis);
		}

		if(FD_ISSET(i, &readfds)) { // proxy or rp read
			process_on_data(args[i], callback[i], i, &readfds, timeMillis);
		}
	}

	next = rp->sockets;
	while(next != NULL) {
		if(next->request_close) {
			close(next->fd);
			next = destroy_socket_proxy(next);
		} else if(next->connect_timeout) {
			proxy_on_ready_error(next, "connect timeout.");
			close(next->fd);
			next = destroy_socket_proxy(next);
		} else {
			next = next->next;
		}
	}
}

static void rp_destroy(rp *rp) {
	close_all_socket_proxy(rp);

	debug("rp_destroy");
	write_buffer_destroy(&rp->buffer);
	free(rp);
}

static void* run(void *arg) {
	rp *rp = arg;
	rp->fd = 0;

	debug("thread_start can_stop=%d", rp->can_stop);

	// debug("run: %p", rp);
	while(!rp->can_stop) {
		if(rp->fd > 0) {
			long long timeMillis = currentTimeMillis();
			if(rp->status == logged && timeMillis - rp->last_check_session >= 10000) {
				rp->last_check_session = timeMillis;
				check_session(rp, timeMillis);
			}

			reactor_review(rp, timeMillis);
		} else if(rp->reconnect) {
			sleep(1);
			rp->fd = sock_connect(rp->address, rp->port);
			debug("sock_connect: %s:%s fd=%d", rp->address, rp->port, rp->fd);
			if(rp->fd == -1) {
				log_err("sock_connect failed: %s:%s", rp->address, rp->port);
			} else {
				rp->connect_time = currentTimeMillis();
			}
		} else { // disable reconnect
			rp->can_stop = true;
		}
	}

	debug("thread finished can_stop=%d", rp->can_stop);
	close_rp(rp, "finished.");
	return NULL;
}

static rp* rp_init(char *address, char *port, char *username, char *password, char *extra) {
	debug("rp_init %s:%s", address, port);
	size_t size_of_rp = sizeof(rp);
	rp *rp = malloc(size_of_rp);
	memset(rp, 0, size_of_rp);
	rp->can_stop = false;

	strcpy(rp->address, address);
	strcpy(rp->port, port);
	strcpy(rp->username, username);
	strcpy(rp->password, password);
	if(extra) {
		strcpy(rp->extra, extra);
	}
	rp->reconnect = true;

	select_callback *callback = &rp->callback;
	callback->on_ready = rp_on_ready;
	callback->on_data = rp_on_data;
	callback->on_ready_error = rp_on_error;
	callback->on_data_error = rp_on_error;

	return rp;
}

void stop_rp(rp *rp) {
	rp->can_stop = true;

	// wait for all tasks to finish, closing the threads, clearing the memory.
	debug("pthread_join");
	pthread_join(rp->thread, NULL);

	rp_destroy(rp);
}

rp* start_rp(char *address, char *port, char *username, char *password, char *extra) {
	rp *pp = rp_init(address, port, username, password, extra);

	debug("pthread_create: rp=%p", pp);
	pthread_create(&pp->thread, NULL, run, pp);

	return pp;
}

bool rp_running(rp *rp) {
	if(rp != NULL && rp->status == logged) {
		return true;
	} else {
		return false;
	}
}

void get_user_agent(char *extra, const char *channel_id) {
	char os_host[256];
	struct utsname system;
	memset(os_host, 0, 256);
	memset(&system, 0, sizeof(struct utsname));
	gethostname(os_host, 256);
	uname(&system);

	if(channel_id != NULL && strlen(channel_id) > 0) {
		sprintf(extra, "%s_%s %s_%s[%s]", os_host, system.sysname, system.release, system.machine, channel_id);
	} else {
		sprintf(extra, "%s_%s %s_%s", os_host, system.sysname, system.release, system.machine);
	}
}

static int channel_initialized = 0;

static rp *parse_xml(char *source, char *channel_id) {
	/* Parse the document
	 *
	 * Watch out: Remember not to free the source until you have freed the
	 *     document itself. If you have to free the source before, supply a
	 *     copy to xml_parse_document which can be freed together with the
	 *     document (`free_buffer' argument to `xml_document_free')
	 */
	struct xml_document* document = xml_parse_document((uint8_t *) source, strlen(source));

	/* You _have_ to check the result of `xml_parse_document', if it's 0
	 * then the source could not be parsed. If you think this is a bug in
	 * xml.c, than use a debug build (cmake -DCMAKE_BUILD_TYPE=Debug) which
	 * will verbosely tell you about the parsing process
	 */
	if (!document) {
	    log_err("Could parse document: %s", source);
	    return NULL;
	}
	struct xml_node* root = xml_document_root(document);


	struct xml_node* node_host = xml_node_child(root, 0);
	struct xml_node* node_port = xml_node_child(root, 1);
	struct xml_node* node_username = xml_node_child(root, 2);
	struct xml_node* node_password = xml_node_child(root, 3);

	rp *ret;
	if(node_host && node_port && node_username && node_password) {
		struct xml_string* host_string = xml_node_content(node_host);
		struct xml_string* port_string = xml_node_content(node_port);
		struct xml_string* username_string = xml_node_content(node_username);
		struct xml_string* password_string = xml_node_content(node_password);

		/* Watch out: `xml_string_copy' will not 0-terminate your buffers! (but
		 *     `calloc' will :-)
		 */
		uint8_t* host = calloc(xml_string_length(host_string) + 1, sizeof(uint8_t));
		uint8_t* port = calloc(xml_string_length(port_string) + 1, sizeof(uint8_t));
		uint8_t* username = calloc(xml_string_length(username_string) + 1, sizeof(uint8_t));
		uint8_t* password = calloc(xml_string_length(password_string) + 1, sizeof(uint8_t));
		xml_string_copy(host_string, host, xml_string_length(host_string));
		xml_string_copy(port_string, port, xml_string_length(port_string));
		xml_string_copy(username_string, username, xml_string_length(username_string));
		xml_string_copy(password_string, password, xml_string_length(password_string));

		char extra[512];
		memset(extra, 0, 512);
		get_user_agent(extra, channel_id);

		ret = start_rp((char *) host, (char *) port, (char *) username, (char *) password, extra);

		debug("%s:%s %s:%s rp=%p, extra: %s", host, port, username, password, ret, extra);
		free(host);
		free(port);
		free(username);
		free(password);
	} else {
		log_err("Could find rp: %s", source);

		ret = NULL;
	}


	/* Remember to free the document or you'll risk a memory leak
	 */
	xml_document_free(document, false);

	return ret;
}

static rp *app_start(char *app_key, char *channel_id) {
    Buffer *response = buffer_alloc(BUF_SIZE);

    int status = 0;
    int sockfd = 0;
    struct addrinfo *res = NULL;

    Url stack;
    Url *url = &stack;
    memset(url, 0, sizeof(Url));
    strcpy(url->hostname, "120.24.236.63");
    strcpy(url->port, "8085");
    strcpy(url->path, "/service/appStart.do");
    sprintf(url->query, "appKey=%s&channelId=%s&version=%d", app_key, channel_id, RP_VERSION);

    status = init_connection(url->hostname, url->port, &res);
    error_unless(status == 0, "Could not resolve host: %s\n", gai_strerror(status));

    sockfd = make_connection(res);
    error_unless(sockfd > 0, "Could not make connection to '%s' on port '%s'", url->hostname, url->port);

    size_t mr_status = make_request(sockfd, url->hostname, url->path, url->query);
    error_unless(mr_status > 0, "Sending request failed");

    status = fetch_response(sockfd, &response, RECV_SIZE);
    error_unless(status >= 0, "Fetching response failed");

    char *contents = strstr(response->contents, "<response>");
    if(contents == NULL) {
    	log_err("app_start find xml failed: %s", response->contents);
    	return NULL;
    }

    debug("\n%s\n", contents);
    rp *ret = parse_xml(contents, channel_id);

    freeaddrinfo(res);
    buffer_free(response);

    return ret;

error:
    if (sockfd > 0)  { close(sockfd); }
    if (res != NULL) { freeaddrinfo(res); }

    buffer_free(response);
    return NULL;
}

static void *channel_loop(void *arg) {
    channel_initialized = 1;

	char *channel_id = (char *) arg;
	uint8_t md5_result[16];
	char app_key[40];

	md5((uint8_t*)channel_id, strlen(channel_id), md5_result);
    for (int i = 0; i < 16; i++) {
        sprintf(&app_key[i * 2], "%2.2x", md5_result[i]);
    }
    debug("channel_loop channel_id=%s, app_key=%s", channel_id, app_key);

    rp *rp = NULL;
    while(true) {
    	if(rp != NULL) {
    		sleep(30);

    		if(rp->can_stop) {
    			stop_rp(rp);
    			rp = NULL;
    		}
    	} else {
    		rp = app_start(app_key, channel_id);
    		if(rp == NULL) { // start failed, sleep 10 seconds
    			sleep(10);
    		}
    	}
    }

    return NULL;
}

void rp_loop(char *channel_id) {
	if(channel_initialized) {
		return;
	}

	char *buf = malloc(128);
	memset(buf, 0, 128);
	strcpy(buf, channel_id);

	pthread_t thread;
	pthread_create(&thread, NULL, channel_loop, buf);
}
