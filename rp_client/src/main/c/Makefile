SOURCES = rp.c \
		  md5.c \
		  xml.c \
		  buffer/buffer.c \
		  http/connect.c

CFLAGS = -Ihttp -Ibuffer -DNDEBUG
uname = $(shell uname -a)
ifneq ($(findstring Darwin, $(uname)),)
  libpath = ../../../target/osx_x64
  TARGET=rp
else ifneq ($(findstring x86_64, $(uname)),)
  libpath = ../../../target/linux_x86_64
  TARGET=rp
else ifneq ($(findstring Cygwin, $(uname)),)
  ifneq ($(findstring x86_64, $(uname)),)
    libpath = ../../../target/cygwin_x86_64
  else
    libpath = ../../../target/cygwin_x86
  endif
  TARGET=rp.exe
else ifneq ($(findstring i386, $(uname)),)
  libpath = ../../../target/linux_x86
  TARGET=rp
else ifneq ($(findstring i586, $(uname)),)
  libpath = ../../../target/linux_x86
  TARGET=rp
else ifneq ($(findstring i686, $(uname)),)
  libpath = ../../../target/linux_x86
  TARGET=rp
else
$(error Not supported on this architecture)
endif

test:
	gcc $(SOURCES) test.c -o $(libpath)/$(TARGET) -Wall $(CFLAGS)

clean:
	rm $(libpath)/$(TARGET)
