//
//  ReverseProxy.h
//  ReverseProxy
//
//  Created by Banny on 2024/11/1.
//

#import <Foundation/Foundation.h>
#import <ReverseProxy/rp.h>

//! Project version number for ReverseProxy.
FOUNDATION_EXPORT double ReverseProxyVersionNumber;

//! Project version string for ReverseProxy.
FOUNDATION_EXPORT const unsigned char ReverseProxyVersionString[];

// In this header, you should import all the public headers of your framework using statements like #import <ReverseProxy/PublicHeader.h>

@interface ReverseProxy : NSObject

@property(nonatomic, strong) NSString *ip;
@property(nonatomic, strong) NSString *port;

-(id) initWithServer: (NSString *) ip port : (NSString *) port;

-(rp *) loginWithUsername: (NSString *) username password : (NSString *) password extraData : (NSString *) extra;

@end
