LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
# Should be for all types of releases later
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := evscamera
LOCAL_SRC_FILES := $(LOCAL_MODULE).apk
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_PRIVILEGED_MODULE := true
LOCAL_CERTIFICATE := PRESIGNED

LOCAL_REQUIRED_MODULES := privapp_permissions_se.cpacsystems.carevscamera.xml

include $(BUILD_PREBUILT)

############# Priv app permissions white list ################
include $(CLEAR_VARS)

LOCAL_MODULE := privapp_permissions_se.cpacsystems.carevscamera.xml

LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_CLASS := ETC
LOCAL_PRIVILEGED_MODULE := true
LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)/permissions

include $(BUILD_PREBUILT)
