@echo off
setlocal
set "MAVEN_PROJECTBASEDIR=%~dp0."
set "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"

if defined JAVA_HOME (
  "%JAVA_HOME%\bin\java.exe" -Duser.home="%USERPROFILE%" -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
) else (
  java -Duser.home="%USERPROFILE%" -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
)
exit /b %ERRORLEVEL%
