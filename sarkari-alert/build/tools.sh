# Toolchain paths: heavy jars come from the repo itself (survive machine resets)
export JAVA=/tmp/jenv/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java
export ECJ="$ROOT/tools/ecj.jar"
export DX_JAR="$ROOT/tools/dx.jar"
export AAPT2=/tmp/aaptjs3/package/bin/x64/linux/aapt2
export ANDROID_JAR=/tmp/android-platforms/android-28/android.jar
export KEY_DIR="$ROOT/keys"
