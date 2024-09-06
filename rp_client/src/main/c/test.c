/*
 * test.c
 *
 *  Created on: 2016年12月16日
 *      Author: zhkl0228
 */

#include <sys/utsname.h>
#include <stdio.h>
#include <string.h>
#include "rp.h"
#include "md5.h"
#include "xml.h"

#include <buffer.h>
#include "connect.h"

static void test_xml() {
	/* XML source, could be read from disk
	 */
	char *source = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
	    "<Root>"
	        "<Hello>World</Hello>"
	        "<This>"
	            "<Is>:-)</Is>"
	            "<An>:-O</An>"
	            "<Example>:-D</Example>"
	        "</This>"
	    "</Root>"
	;


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
	    printf("Could parse document\n");
	    return;
	}
	struct xml_node* root = xml_document_root(document);


	/* Say Hello World :-)
	 */
	struct xml_node* root_hello = xml_node_child(root, 0);
	struct xml_string* hello = xml_node_name(root_hello);
	struct xml_string* world = xml_node_content(root_hello);

	/* Watch out: `xml_string_copy' will not 0-terminate your buffers! (but
	 *     `calloc' will :-)
	 */
	uint8_t* hello_0 = calloc(xml_string_length(hello) + 1, sizeof(uint8_t));
	uint8_t* world_0 = calloc(xml_string_length(world) + 1, sizeof(uint8_t));
	xml_string_copy(hello, hello_0, xml_string_length(hello));
	xml_string_copy(world, world_0, xml_string_length(world));

	printf("%s %s\n", hello_0, world_0);
	free(hello_0);
	free(world_0);


	/* Extract amount of Root/This children
	 */
	struct xml_node* root_this = xml_node_child(root, 1);
	printf("Root/This has %lu children\n", (unsigned long)xml_node_children(root_this));


	/* Remember to free the document or you'll risk a memory leak
	 */
	xml_document_free(document, false);
}

static void http_test()
{
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
    sprintf(url->query, "appKey=%s&channelId=%s&version=%d", "app_key", "channel_id", RP_VERSION);

    status = init_connection(url->hostname, url->port, &res);
    error_unless(status == 0, "Could not resolve host: %s\n", gai_strerror(status));

    sockfd = make_connection(res);
    error_unless(sockfd > 0, "Could not make connection to '%s' on port '%s'", url->hostname, url->port);

    status = make_request(sockfd, url->hostname, url->path, url->query);
    error_unless(status > 0, "Sending request failed");

    status = fetch_response(sockfd, &response, RECV_SIZE);
    error_unless(status >= 0, "Fetching response failed");

    printf("%s\n", response->contents);

    close(sockfd);

    freeaddrinfo(res);
    buffer_free(response);

    return;

error:
    if (sockfd > 0)  { close(sockfd); }
    if (res != NULL) { freeaddrinfo(res); }

    buffer_free(response);
}

int main(int argc, char const *argv[]) {
	char *host = "localhost";

	uint8_t result[16];

	rp *rp = NULL;

	while(true) {
		char cmd[64];
		scanf("%s", cmd);

		if(!strcasecmp(cmd, "loop")) {
			rp_loop("ansic");
		}

		if(!strcasecmp(cmd, "start") && rp == NULL) {
			rp = start_rp(host, "2016", "test", "test_pass", NULL);
		}

		if(!strcasecmp(cmd, "exit")) {
			break;
		}

		if(!strcasecmp(cmd, "running")) {
			printf("running: %d\n", rp_running(rp));
		}

		if(!strcasecmp(cmd, "md5")) {
			md5((uint8_t*)host, strlen(host), result);
		    for (int i = 0; i < 16; i++) {
		        printf("%2.2x", result[i]);
		    }
		    printf("\n");
		}

		if(!strcasecmp(cmd, "xml")) {
			test_xml();
		}

		if(!strcasecmp(cmd, "http")) {
			http_test();
		}

		if(!strcasecmp(cmd, "sys")) {
			char host[1024];
			struct utsname system;
			gethostname(host, 1024);
			uname(&system);

			printf("Host: %s\n", host);
			printf("System: %s %s.%s\n", system.sysname, system.release, system.machine);
		}
	}

	if(rp) {
		stop_rp(rp);
	}
	return 0;
}
