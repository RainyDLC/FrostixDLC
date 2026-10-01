cmake_minimum_required(VERSION 3.20)
project(jar_to_dll LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
add_executable(jar-to-dll
    src/main.cpp
    src/core/builder.cpp
    src/core/codegen.cpp
    src/core/jar.cpp
)
target_include_directories(jar-to-dll PRIVATE src)
set(JVMTI_TABLE_HEADER "${CMAKE_CURRENT_SOURCE_DIR}/../runtime/native/JvmtiMethodTable.hpp")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${JVMTI_TABLE_HEADER}")
file(READ "${JVMTI_TABLE_HEADER}" JVMTI_TABLE_SOURCE)
configure_file(src/core/jvmti_table_source.h.in generated/jvmti_table_source.h @ONLY)
set_property(SOURCE src/core/codegen.cpp APPEND PROPERTY OBJECT_DEPENDS
    "${CMAKE_CURRENT_BINARY_DIR}/generated/jvmti_table_source.h;${JVMTI_TABLE_HEADER}")
target_include_directories(jar-to-dll PRIVATE "${CMAKE_CURRENT_BINARY_DIR}/generated")
