cmake_minimum_required(VERSION 3.22.1)
project(howler_audio)

# Oboe arrives as a prefab package from the com.google.oboe:oboe AAR.
find_package(oboe REQUIRED CONFIG)

add_library(howler_audio SHARED audio_engine.cpp)

target_compile_features(howler_audio PRIVATE cxx_std_17)

# Android 16+ devices (Pixel 9/10) run 16KB memory pages. NDK r27's default
# 4KB segment alignment makes the dynamic linker reject the .so on load →
# crash on open. Force 16KB ELF segment alignment.
target_link_options(howler_audio PRIVATE "-Wl,-z,max-page-size=16384")

target_link_libraries(howler_audio oboe::oboe android log)
