#!/usr/bin/env python
# -*- coding: utf-8 -*-

'''
Created on Feb 22, 2016

@author: zhkl0228
'''

import sys;
import threading;
import struct;
import socket;
import time;

debug = False;
VERSION = 0x30011;

def currentTimeMillis():
  return int(round(time.time() * 1000));

class __Forward(object):
  def __init__(self, remotePort, toHost, toPort):
    self.__remotePort = remotePort;
    self.__toHost = toHost;
    self.__toPort = toPort;
  def bind(self, client):
    client.requestForward(self.__remotePort, self.__toHost, self.__toPort);
    
class __RemoteServer(object):
  def __init__(self, host, port, username, password, extraData, changeIp):
    self.__host = host;
    self.__port = port;
    self.__username = username;
    self.__password = password;
    self.__extraData = extraData;
    self.__changeIp = changeIp;
    self.__forwards = [];
  def addForward(self, forward):
    self.__forwards.append(forward);
  def createAndLogin(self):
    client = ReverseProxyClient(self.__host, self.__port, self.__extraData, self.__changeIp, authListener=self);
    client.login(self.__username, self.__password);
    return client;
  def onAuthResponse(self, client, status):
    if status != 0:
      return True;
    for f in self.__forwards:
      f.bind(client);
    return False;

class DataInput(object):
  def __init__(self, buf):
    self.__index = 0;
    self.__buffer = buf;
    self.command = self.readByte();
  def readByte(self):
    val = ord(self.__buffer[self.__index]);
    self.__index += 1;
    return val;
  def readBoolean(self):
    val = self.__buffer[self.__index];
    self.__index += 1;
    return ord(val) != 0;
  def readShort(self):
    val, = struct.unpack(">h", self.__buffer[self.__index:self.__index+2]);
    self.__index += 2;
    return val;
  def readUnsignedShort(self):
    return self.readShort() & 0xFFFF;
  def readInt(self):
    val, = struct.unpack(">i", self.__buffer[self.__index:self.__index+4]);
    self.__index += 4;
    return val;
  def readUTF(self):
    length = self.readShort();
    val = self.__buffer[self.__index:self.__index+length].encode("UTF-8");
    self.__index += length;
    return val;
  def readLong(self):
    val, = struct.unpack(">q", self.__buffer[self.__index:self.__index+8]);
    self.__index += 8;
    return val;
  def hasRemaining(self):
    return self.remaining() > 0;
  def remaining(self):
    return len(self.__buffer) - self.__index;
  
class DataOutput(object):
  def __init__(self, command):
    self.__data = [];
    self.__data.append(chr(command));
  def writeUTF(self, val):
    val = val.decode("UTF-8");
    self.__data.append(struct.pack(">h", len(val)));
    self.__data.append(val);
  def writeBoolean(self, flag):
    if flag:
      self.__data.append(chr(1));
    else:
      self.__data.append(chr(0));
  def writeInt(self, val):
    self.__data.append(struct.pack(">i", val));
  def writeLong(self, val):
    self.__data.append(struct.pack(">q", val));
  def writeShort(self, val):
    self.__data.append(struct.pack(">h", val));
  def pack(self):
    data = [];
    length = 0;
    for x in self.__data:
      length += len(x);
    data.append(struct.pack(">i", length));
    for x in self.__data:
      data.append(x);
    return data;
  
class ReverseProxyClient(object):
  def __init__(self, host, port, extraData, changeIp=None, authListener=None):
    self.__host = host;
    self.__port = port;
    self.__extraData = extraData;
    self.__changeIp = changeIp if not changeIp is None and len(changeIp.strip()) > 0 else None;
    self.__authListener = authListener;
    self.__reconnect = True;
    self.__networkDelay = 0;
    self.__status = 1;
    self.__lastCheckSession = 0;
  
  def __send(self, data):
    for s in data:
      self.__socket.sendall(s);
    
  def requestAuth(self):
    out = DataOutput(0x7);
    out.writeUTF(self.__username);
    out.writeUTF(self.__password);
    out.writeInt(VERSION);
    out.writeBoolean(not self.__extraData is None);
    if not self.__extraData is None:
      out.writeUTF(self.__extraData);
    out.writeBoolean(not self.__changeIp is None);
    if not self.__changeIp is None:
      out.writeUTF(self.__changeIp);
    out.writeLong(currentTimeMillis());
    out.writeBoolean(False);
    self.__send(out.pack());
  def requestForward(self, remotePort, host, port):
    out = DataOutput(0xA);
    out.writeShort(remotePort);
    out.writeUTF(host);
    out.writeShort(port);
    self.__send(out.pack());
  def __check(self):
    while not self.__can_stop:
      if self.__status != 0:
        time.sleep(1);
        continue;
      out = DataOutput(0x8);
      out.writeInt(self.__networkDelay);
      out.writeLong(currentTimeMillis());
      out.writeInt(0);
      self.__send(out.pack());
      time.sleep(10);
  
  def __processPacket(self, data):
    packet = DataInput(data);
    if packet.command == 0x7:
      self.__status = packet.readByte();
      msg = packet.readUTF() if packet.readBoolean() else None;
      expire = packet.readLong() if packet.readBoolean() else None;
      nick = packet.readUTF() if packet.readBoolean() else None;
      self.__reconnect = packet.readBoolean() if packet.hasRemaining() else True;
      self.__networkDelay = (currentTimeMillis() - packet.readLong()) if packet.remaining() >= 8 else 0;
      if self.__status != 0 and not msg is None:
        print self.__thread.name, msg;
      if self.__status == 0:
        print self.__thread.name, "登录成功";
      if debug:
        print "auth:", self.__status, msg, expire, nick, self.__reconnect, self.__networkDelay;
      if self.__authListener and self.__authListener.onAuthResponse(self, self.__status):
        self.__reconnect = False;
        self.close();
      return;
    
    if packet.command == 0x8:
      self.__networkDelay = currentTimeMillis() - packet.readLong();
      return;
    
    if packet.command == 0xA:
      remotePort = packet.readUnsignedShort();
      if packet.readByte() == 0:
        print "Bind port", remotePort, "successfully.";
      else:
        print "Bind port", remotePort, "failed.";
      return;
    
    if debug:
      print [hex(ord(x)) for x in data];
    
  def __run(self):
    try:
      self.__socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM);
      self.__socket.settimeout(10);
      self.__socket.connect((self.__host, self.__port));
      self.__socket.settimeout(None);
      self.requestAuth();
      while not self.__can_stop:
        length, = struct.unpack(">i", self.__socket.recv(4));
        data = self.__socket.recv(length);
        self.__processPacket(data);
        
    except StandardError, e:
      print e, self.__thread.name;
    finally:
      self.__thread = None;
      if self.__socket:
        self.__socket.close();
    
    if not self.__can_stop and self.__reconnect:
      self.__execute_login();
    
  def __execute_login(self):
    self.__thread = threading.Thread(target=self.__run, name=host + ':' + port);
    self.__thread.setDaemon(True);
    self.__thread.start();
    
    self.__checker = threading.Thread(target=self.__check);
    self.__checker.setDaemon(True);
    self.__checker.start();
    
  def login(self, username, password):
    self.__username = username;
    self.__password = password;
    self.__can_stop = False;
    self.__execute_login();
    
  def close(self):
    self.__can_stop = True;
    if self.__socket:
      self.__socket.close();

if __name__ == '__main__':
  import xml.dom.minidom;
  dom = xml.dom.minidom.parse("config.xml");
  element = dom.documentElement;
  isDebug = element.getAttribute("debug");
  if isDebug == "true":
    debug = True;
  
  servers = [];
  clients = [];
  for s in element.getElementsByTagName("server"):
    host = s.getAttribute("host");
    port = s.getAttribute("port");
    username = s.getAttribute("username");
    password = s.getAttribute("password");
    extra = s.getAttribute("extra");
    changeIp = s.getAttribute("changeIp");
    if host is None or port is None or username is None or password is None:
      continue;
    server = __RemoteServer(host, int(port), username, password, extra, changeIp);
    for f in s.getElementsByTagName("forward"):
      remotePort = f.getAttribute("remotePort");
      toHost = f.getAttribute("toHost");
      toPort = f.getAttribute("toPort");
      if remotePort is None or toHost is None or toPort is None:
        continue;
      server.addForward(__Forward(int(remotePort), toHost, int(toPort)));
    servers.append(server);
  if len(servers) < 1:
    print "No servers in config.xml";
    sys.exit(1);
  
  for server in servers:
    clients.append(server.createAndLogin());
  
  while True:
    line = sys.stdin.readline();
    if not line:
      break;
    
    line = line.strip().lower();
    if debug:
      print "command:", line;
    if line == "exit" or line == "quit":
      break;
  
  for client in clients:
    client.close();
  
  print "exit successfully!";
