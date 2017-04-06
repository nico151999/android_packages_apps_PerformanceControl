LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
        $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := PerformanceControl
#LOCAL_CERTIFICATE := platform

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v4 android-support-v13

include $(BUILD_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))

# Reduce ROM size
LOCAL_OVERRIDES_PACKAGES := Browser
LOCAL_OVERRIDES_PACKAGES := Gello
LOCAL_OVERRIDES_PACKAGES := Camera2
LOCAL_OVERRIDES_PACKAGES := Gallery2
LOCAL_OVERRIDES_PACKAGES := Eleven
LOCAL_OVERRIDES_PACKAGES := Email
LOCAL_OVERRIDES_PACKAGES := Exchange
LOCAL_OVERRIDES_PACKAGES := Exchange2
LOCAL_OVERRIDES_PACKAGES := Snap
