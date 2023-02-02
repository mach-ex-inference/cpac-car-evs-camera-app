# CPAC EVS Camera App

CPAC specific EvsCameraApp for usage on platforms that support multi-stream video through
the CarEvs 1.1 interface.


---


## Usage


### Start from launcher
Click on the CarEvsCamera icon.


### Start from command line
Issue a launch Intent from command line using adb.

```
    $ adb shell am start -n se.cpacsystems.carevscamera/se.cpacsystems.carevscamera.MainActivity
```


---



## Developers


### Permissions
The app uses ```android.car``` API and requires the following permissions.

  - ```android.car.permission.REQUEST_CAR_EVS_ACTIVITY```
  - ```android.car.permission.CONTROL_CAR_EVS_ACTIVITY```
  - ```android.car.permission.USE_CAR_EVS_CAMERA```
  - ```android.car.permission.MONITOR_CAR_EVS_STATUS```


### Integrate App Into Platform
To get correct signing and permissions for the application it is usually neccessary to deploy the application as a prebuild app with the platform. This is done by creating a new module and adding a dependency in the target build.
Creating a new module is done by copying the build artifact, a make-file and a xml-file containing required permissions to a vendor specific folder.


#### Creating Keystore & Setting up Signing Config
Create a set of signing keys for the app by using the ```keytool``` in the application project root folder.

```
    $ keytool -genkey -v -keystore release.keystore -storepass android -alias android -keypass android -keyalg RSA -keysize 2048 -validity 10000
```

If you change keystore name, alias or key password, make sure to change ```signingConfigs.release``` in ```app/build.gradle```.


#### Setup Paths for Creating Module
```gradle.properties``` should contain paths to the AOSP project folder as well as the vendor-specific 
sub-folder.

```
    aosp.home=<Insert path to AOSP project folder here>
    vendor.prebuilt=<Insert sub-path to vendor specific prebuild folder here>
```


#### Copy Artifacts to Platform directory to Create a New Module
To build, copy artifact, makefile and permission-definitions to the aosp vendor specific folder use
the gradle command ```makeModule```.

Example:
```
    $ ./gradlew makeModule
```


#### Add The Module to the Build Target.
Find the target ```Android.mk``` and append the evscamera as a required module.

```
LOCAL_REQUIRED_MODULES := \
   [...]
   evscamera \
```

---


## License
Copyright 2023 CPAC Systems AB

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
 
http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
