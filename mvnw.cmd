@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM Apache Maven Wrapper startup batch script, version 3.3.2

@echo off
setlocal enabledelayedexpansion

SET MAVEN_WRAPPER_PROPERTIES=%~dp0.mvn\wrapper\maven-wrapper.properties
SET MAVEN_USER_HOME=%USERPROFILE%\.m2
SET MAVEN_HOME_PARENT=%MAVEN_USER_HOME%\wrapper\dists

FOR /F "tokens=2 delims==" %%G IN ('findstr /I "distributionUrl" "%MAVEN_WRAPPER_PROPERTIES%"') DO (
    SET DISTRIBUTION_URL=%%G
)

FOR %%F IN (%DISTRIBUTION_URL%) DO SET DIST_FILENAME=%%~nxF
SET DIST_NAME=%DIST_FILENAME:-bin.zip=%
SET MAVEN_HOME=%MAVEN_HOME_PARENT%\%DIST_NAME%\%DIST_NAME%

IF NOT EXIST "%MAVEN_HOME%" (
    echo Downloading Apache Maven %DIST_NAME%...
    mkdir "%MAVEN_HOME_PARENT%\%DIST_NAME%" 2>NUL
    powershell -Command "Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%MAVEN_HOME_PARENT%\%DIST_NAME%\%DIST_FILENAME%'"
    powershell -Command "Expand-Archive -Path '%MAVEN_HOME_PARENT%\%DIST_NAME%\%DIST_FILENAME%' -DestinationPath '%MAVEN_HOME_PARENT%\%DIST_NAME%'"
    del "%MAVEN_HOME_PARENT%\%DIST_NAME%\%DIST_FILENAME%"
    echo Done.
)

"%MAVEN_HOME%\bin\mvn.cmd" %*