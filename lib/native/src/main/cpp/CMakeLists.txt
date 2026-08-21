cmake_minimum_required(VERSION 3.22.1)
project(tiffrenderer_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

option(TIFFRENDERER_WERROR "Fail the build on any -Wall/-Wextra warning in this project's own targets (not vendored third_party code)" OFF)
option(TIFFRENDERER_FUZZ "Build the tiff_fuzz libFuzzer harness, ASan/UBSan-instrumented (Clang only)" OFF)

if(TIFFRENDERER_FUZZ)
    if(NOT CMAKE_CXX_COMPILER_ID MATCHES "Clang")
        message(FATAL_ERROR "TIFFRENDERER_FUZZ requires Clang (libFuzzer/-fsanitize=fuzzer)")
    endif()
    # fuzzer-no-link instruments every target below (including vendored libtiff/libjpeg/libwebp)
    # with libFuzzer's coverage counters without providing a main(), since that would collide
    # across this many static libraries. Only tiff_fuzz itself links plain -fsanitize=fuzzer,
    # which provides the actual entry point, further down this file.
    add_compile_options(-fsanitize=fuzzer-no-link,address,undefined -fno-sanitize-recover=undefined -g -O1)
    add_link_options(-fsanitize=address,undefined -fno-sanitize-recover=undefined)
endif()

if(NOT MSVC)
    # The NDK and modern glibc toolchains (glibc >= 2.35 defaults to 3) may already define
    # _FORTIFY_SOURCE, so -U first avoids a -Wmacro-redefined warning; level 3 needs GCC >= 12 or
    # Clang >= 15, older toolchains only support 2. Both go in one add_compile_options call: CMake
    # doesn't guarantee ordering between separate add_compile_definitions/add_compile_options calls.
    set(TIFFRENDERER_FORTIFY_LEVEL 2)
    if((CMAKE_CXX_COMPILER_ID STREQUAL "GNU" AND CMAKE_CXX_COMPILER_VERSION VERSION_GREATER_EQUAL 12)
            OR (CMAKE_CXX_COMPILER_ID MATCHES "Clang" AND CMAKE_CXX_COMPILER_VERSION VERSION_GREATER_EQUAL 15))
        set(TIFFRENDERER_FORTIFY_LEVEL 3)
    endif()
    add_compile_options(
        -fvisibility=hidden -fvisibility-inlines-hidden -fstack-protector-strong
        $<$<NOT:$<CONFIG:Debug>>:-U_FORTIFY_SOURCE>
        $<$<NOT:$<CONFIG:Debug>>:-D_FORTIFY_SOURCE=${TIFFRENDERER_FORTIFY_LEVEL}>
    )
else()
    add_compile_options(/guard:cf)
    add_link_options(/guard:cf)
endif()

if(UNIX AND NOT APPLE AND NOT ANDROID)
    find_library(TIFFRENDERER_STATIC_ZLIB NAMES libz.a)
    if(TIFFRENDERER_STATIC_ZLIB)
        set(ZLIB_LIBRARY ${TIFFRENDERER_STATIC_ZLIB} CACHE FILEPATH "" FORCE)
    else()
        find_library(TIFFRENDERER_SHARED_ZLIB NAMES libz.so)
        if(TIFFRENDERER_SHARED_ZLIB)
            set(ZLIB_LIBRARY ${TIFFRENDERER_SHARED_ZLIB} CACHE FILEPATH "" FORCE)
        endif()
    endif()
endif()

# --- Vendored libtiff (submodule, pinned tag, unpatched); codec set matches README's support table ---
set(tiff-static ON CACHE BOOL "" FORCE)
set(tiff-tools OFF CACHE BOOL "" FORCE)
set(tiff-tests OFF CACHE BOOL "" FORCE)
set(tiff-contrib OFF CACHE BOOL "" FORCE)
set(tiff-docs OFF CACHE BOOL "" FORCE)
set(tiff-deprecated OFF CACHE BOOL "" FORCE)
set(tiff-install OFF CACHE BOOL "" FORCE)

set(zlib ON CACHE BOOL "" FORCE)
set(libdeflate OFF CACHE BOOL "" FORCE)
set(jpeg ON CACHE BOOL "" FORCE)
set(old-jpeg OFF CACHE BOOL "" FORCE)  # legacy pre-TIFF6 JPEG compression, out of scope
set(webp ON CACHE BOOL "" FORCE)
set(zstd OFF CACHE BOOL "" FORCE)
set(lerc OFF CACHE BOOL "" FORCE)
set(lzma OFF CACHE BOOL "" FORCE)
set(jbig OFF CACHE BOOL "" FORCE)

# --- Vendored plain IJG libjpeg (not libjpeg-turbo, which refuses add_subdirectory(); ships no build system of its own, so this target is hand-defined) ---
add_library(ijg_jpeg STATIC
    third_party/libjpeg/jcapimin.c
    third_party/libjpeg/jcapistd.c
    third_party/libjpeg/jdapimin.c
    third_party/libjpeg/jdapistd.c
    third_party/libjpeg/jcomapi.c
    third_party/libjpeg/jcparam.c
    third_party/libjpeg/jctrans.c
    third_party/libjpeg/jdtrans.c
    third_party/libjpeg/jcinit.c
    third_party/libjpeg/jcmaster.c
    third_party/libjpeg/jcmainct.c
    third_party/libjpeg/jcprepct.c
    third_party/libjpeg/jccoefct.c
    third_party/libjpeg/jccolor.c
    third_party/libjpeg/jcsample.c
    third_party/libjpeg/jcdctmgr.c
    third_party/libjpeg/jfdctint.c
    third_party/libjpeg/jfdctfst.c
    third_party/libjpeg/jfdctflt.c
    third_party/libjpeg/jchuff.c
    third_party/libjpeg/jcarith.c
    third_party/libjpeg/jcmarker.c
    third_party/libjpeg/jdatadst.c
    third_party/libjpeg/jdmaster.c
    third_party/libjpeg/jdinput.c
    third_party/libjpeg/jdmainct.c
    third_party/libjpeg/jdcoefct.c
    third_party/libjpeg/jdpostct.c
    third_party/libjpeg/jdmarker.c
    third_party/libjpeg/jdhuff.c
    third_party/libjpeg/jdarith.c
    third_party/libjpeg/jddctmgr.c
    third_party/libjpeg/jidctint.c
    third_party/libjpeg/jidctfst.c
    third_party/libjpeg/jidctflt.c
    third_party/libjpeg/jdsample.c
    third_party/libjpeg/jdcolor.c
    third_party/libjpeg/jdmerge.c
    third_party/libjpeg/jquant1.c
    third_party/libjpeg/jquant2.c
    third_party/libjpeg/jdatasrc.c
    third_party/libjpeg/jaricom.c
    third_party/libjpeg/jerror.c
    third_party/libjpeg/jmemmgr.c
    third_party/libjpeg/jutils.c
    third_party/libjpeg/jmemnobs.c
)
target_include_directories(ijg_jpeg PUBLIC
    third_party/libjpeg
    jconfig
)
# Alias to the name libtiff's JPEGCodec.cmake/find_package(JPEG) expects, since it can't see this in-tree target directly.
add_library(JPEG::JPEG ALIAS ijg_jpeg)
set(JPEG_FOUND TRUE CACHE BOOL "" FORCE)
set(JPEG_LIBRARIES "JPEG::JPEG" CACHE STRING "" FORCE)

# Pre-seed JPEGCodec.cmake's libjpeg-turbo dual-mode probe: plain IJG libjpeg never has jpeg12_read_scanlines.
set(HAVE_JPEGTURBO_DUAL_MODE_8 FALSE CACHE INTERNAL "")
set(HAVE_JPEGTURBO_DUAL_MODE_12 FALSE CACHE INTERNAL "")

# --- Vendored libwebp (submodule); only the core codec lib is needed, so every WEBP_BUILD_* tool option is off ---
set(WEBP_BUILD_ANIM_UTILS OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_CWEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_DWEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_GIF2WEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_IMG2WEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_VWEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_WEBPINFO OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_LIBWEBPMUX OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_WEBPMUX OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF)
add_subdirectory(third_party/libwebp EXCLUDE_FROM_ALL)

add_library(WebP::webp ALIAS webp)

# Pre-seed what WebPCodec.cmake's unconditional find_package(WebP) searches for, so it resolves TRUE without a real search.
set(WebP_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/libwebp/src" CACHE PATH "" FORCE)
set(WebP_LIBRARY "unused-webp-alias-placeholder" CACHE FILEPATH "" FORCE)

add_subdirectory(third_party/libtiff EXCLUDE_FROM_ALL)

# --- Platform-neutral core (no JNI, no Android deps); also the iOS cinterop build's link
# --- target, so it's kept as its own CMake target rather than folded into the JNI .so.
add_library(tiffrenderer_core STATIC
    tiff_core.cpp
    tiff_io.cpp
)
target_link_libraries(tiffrenderer_core PUBLIC tiff)
target_include_directories(tiffrenderer_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
if(NOT MSVC)
    target_compile_options(tiffrenderer_core PRIVATE -Wall -Wextra $<$<BOOL:${TIFFRENDERER_WERROR}>:-Werror>)
else()
    target_compile_options(tiffrenderer_core PRIVATE /W4 $<$<BOOL:${TIFFRENDERER_WERROR}>:/WX>)
endif()

# --- JNI wrapper (Android only) -----------------------------------------------
# Guarded so this same CMakeLists.txt also serves as the iOS build's front door (invoked with
# CMAKE_SYSTEM_NAME=iOS, no NDK toolchain file involved, so ANDROID is unset there): that
# invocation only wants tiffrenderer_core, since jni.h/android/bitmap.h don't exist on iOS.
if(ANDROID)
    add_library(tiffrenderer_jni SHARED
        tiff_renderer_jni.cpp
        jni_shared.cpp
    )

    find_library(log-lib log)
    find_library(jnigraphics-lib jnigraphics)

    target_link_libraries(tiffrenderer_jni
        PRIVATE
            tiffrenderer_core
            ${log-lib}
            ${jnigraphics-lib}
    )

    target_compile_options(tiffrenderer_jni PRIVATE -Wall -Wextra $<$<BOOL:${TIFFRENDERER_WERROR}>:-Werror>)
    target_link_options(tiffrenderer_jni PRIVATE
        "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/jni_exports.map"
        "-Wl,-z,relro" "-Wl,-z,now"
    )
endif()

# --- JNI wrapper (desktop JVM only): raw jintArray pixels, jni.h from the JDK via find_package(JNI) ---
if(NOT ANDROID AND NOT CMAKE_SYSTEM_NAME STREQUAL "iOS")
    find_package(JNI REQUIRED)

    add_library(tiffrenderer_jni_jvm SHARED
        tiff_renderer_jni_jvm.cpp
        jni_shared.cpp
    )

    target_include_directories(tiffrenderer_jni_jvm PRIVATE ${JNI_INCLUDE_DIRS})
    target_link_libraries(tiffrenderer_jni_jvm PRIVATE tiffrenderer_core)
    if(NOT MSVC)
        target_compile_options(tiffrenderer_jni_jvm PRIVATE -Wall -Wextra $<$<BOOL:${TIFFRENDERER_WERROR}>:-Werror>)
    else()
        target_compile_options(tiffrenderer_jni_jvm PRIVATE /W4 $<$<BOOL:${TIFFRENDERER_WERROR}>:/WX>)
    endif()
    if(APPLE)
        target_link_options(tiffrenderer_jni_jvm PRIVATE "-Wl,-exported_symbols_list,${CMAKE_CURRENT_SOURCE_DIR}/jni_exports.macos.txt")
    elseif(UNIX)
        target_link_options(tiffrenderer_jni_jvm PRIVATE
            "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/jni_exports.map"
            "-Wl,-z,relro" "-Wl,-z,now"
        )
    endif()
endif()

# --- libFuzzer harness (no JNI, no Android/iOS deps; Clang only) --------------------------------
if(TIFFRENDERER_FUZZ)
    add_executable(tiff_fuzz fuzz/tiff_fuzz.cpp)
    target_link_libraries(tiff_fuzz PRIVATE tiffrenderer_core)
    target_link_options(tiff_fuzz PRIVATE -fsanitize=fuzzer,address,undefined -fno-sanitize-recover=undefined)
endif()
