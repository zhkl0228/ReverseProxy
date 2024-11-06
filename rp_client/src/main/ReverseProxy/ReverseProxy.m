//
//  ReverseProxy.m
//  ReverseProxy
//
//  Created by Banny on 2024/11/6.
//

#import <Foundation/Foundation.h>
#import "ReverseProxy.h"

@implementation ReverseProxy

-(id) initWithServer:(NSString *)ip port:(NSString *)port {
    ReverseProxy *rp = [super init];
    [rp setIp:ip];
    [rp setPort:port];
    return rp;
}

-(rp *) loginWithUsername: (NSString *) username password : (NSString *) password extraData : (NSString *) extra {
    NSString *ip = [self ip];
    NSString *port = [self port];
    return start_rp([ip cStringUsingEncoding:kCFStringEncodingUTF8], [port cStringUsingEncoding:kCFStringEncodingUTF8], [username cStringUsingEncoding:kCFStringEncodingUTF8], [password cStringUsingEncoding:kCFStringEncodingUTF8], [extra cStringUsingEncoding:kCFStringEncodingUTF8]);
}

@end
