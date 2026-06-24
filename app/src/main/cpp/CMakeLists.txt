cmake_minimum_required(VERSION 3.22.1)
project(howler_audio)

# Oboe arrives as a prefab package from the com.google.oboe:oboe AAR.
find_package(oboe REQUIRED CONFIG)

add_library(howler_audio SHARED audio_engine.cpp)

target_compile_features(howler_audio PRIVATE cxx_std_17)
target_link_libraries(howler_audio oboe::oboe android log)
