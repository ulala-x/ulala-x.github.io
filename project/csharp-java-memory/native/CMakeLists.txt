cmake_minimum_required(VERSION 3.10)
project(mockzmq C)

# Set C standard
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Set compiler flags for optimization
set(CMAKE_C_FLAGS_RELEASE "-O3 -DNDEBUG")

# Include directories
include_directories(include)

# Source files
set(SOURCES
    src/mock_zmq.c
)

# Create shared library
add_library(mockzmq SHARED ${SOURCES})

# Set library output name (libmockzmq.so on Linux, mockzmq.dll on Windows)
set_target_properties(mockzmq PROPERTIES
    OUTPUT_NAME "mockzmq"
    VERSION 1.0.0
    SOVERSION 1
)

# Installation rules
install(TARGETS mockzmq
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
    RUNTIME DESTINATION bin
)

install(FILES include/mock_zmq.h
    DESTINATION include
)

# Print configuration
message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")
message(STATUS "C Compiler: ${CMAKE_C_COMPILER}")
message(STATUS "C Flags: ${CMAKE_C_FLAGS}")
