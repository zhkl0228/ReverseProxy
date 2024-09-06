LOCAL_PATH		:= $(call my-dir)

include $(CLEAR_VARS)
SOURCES := rp.c md5.c xml.c \
		  buffer/buffer.c \
		  http/connect.c test.c
LOCAL_MODULE := rp
LOCAL_SRC_FILES := $(SOURCES)
LOCAL_CFLAGS := -O2 -std=gnu99 -g -Ihttp -Ibuffer -DNDEBUG \
	-D_DEFAULT_SOURCE -D_GNU_SOURCE -Wall
LOCAL_LDFLAGS += -pie -fPIE
NDK_APP_DST_DIR := ../../../target/$(TARGET_ARCH_ABI)
include $(BUILD_EXECUTABLE)
NDK_APP_DST_DIR := $(NDK_APP_LIBS_OUT)/$(TARGET_ARCH_ABI)
