cmake_minimum_required(VERSION 3.22.1)
project(tiffrenderer_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --- Vendored libtiff -------------------------------------------------------
# Git submodule pinned to a tag (see `git -C third_party/libtiff describe --tags`), tracking
# https://gitlab.com/libtiff/libtiff.git directly — not a trimmed copy, so its own CMakeLists.txt
# builds unpatched. We only ever build its `tiff` CMake target; tools/test/contrib/docs are
# disabled below via cache variables rather than by removing the directories.
#
# Codec support is intentionally narrowed for this first version: baseline TIFF (uncompressed),
# PackBits, LZW and CCITT G3/G4 fax are all implemented inside libtiff itself, no external
# dependency needed. Deflate/ZIP is enabled against the NDK's bundled zlib (headers + libz.so
# ship with every NDK, so it's effectively free). JPEG-in-TIFF is enabled against the vendored
# plain IJG libjpeg below. WebP, Zstd, LERC, LZMA and JBIG are still disabled because they'd each
# require vendoring another third-party codec library — a TIFF using one of those will fail to
# decode with a clear libtiff error at open time instead of silently misdecoding.
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
set(webp OFF CACHE BOOL "" FORCE)
set(zstd OFF CACHE BOOL "" FORCE)
set(lerc OFF CACHE BOOL "" FORCE)
set(lzma OFF CACHE BOOL "" FORCE)
set(jbig OFF CACHE BOOL "" FORCE)

# --- Vendored plain IJG libjpeg (JPEG-in-TIFF support) ----------------------
# Git submodule pinned to a tag, https://github.com/libjpeg-turbo/ijg.git -- a git mirror of each
# IJG release, used instead of libjpeg-turbo proper because libjpeg-turbo's own CMakeLists.txt
# refuses to be add_subdirectory()'d (see DEVLOG.md for the full reasoning). It ships no build
# system of its own, so the library is defined by hand below: exactly the "JPEG library proper"
# files from this submodule's own filelist.txt, excluding cjpeg/djpeg/jpegtran (standalone tools,
# not part of the library). jmemnobs.c is the memory-manager variant used, over jmemansi.c's
# tmpfile()-based alternative -- no reason for a sandboxed native library to spill to disk.
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

# libtiff's cmake/JPEGCodec.cmake only discovers JPEG via find_package, which can't see this
# in-tree target -- alias it to the exact name libtiff's target_link_libraries() hardcodes instead.
add_library(JPEG::JPEG ALIAS ijg_jpeg)
set(JPEG_FOUND TRUE CACHE BOOL "" FORCE)
set(JPEG_LIBRARIES "JPEG::JPEG" CACHE STRING "" FORCE)

# JPEGCodec.cmake also probes for libjpeg-turbo's dual-mode symbols via a try_compile() that can't
# see our ALIAS target either -- pre-seed both results rather than let the probe fail. Correct
# regardless: plain IJG libjpeg never has jpeg12_read_scanlines.
set(HAVE_JPEGTURBO_DUAL_MODE_8 FALSE CACHE INTERNAL "")
set(HAVE_JPEGTURBO_DUAL_MODE_12 FALSE CACHE INTERNAL "")

add_subdirectory(third_party/libtiff EXCLUDE_FROM_ALL)

# --- JNI wrapper -------------------------------------------------------------
add_library(tiffrenderer_jni SHARED
    tiff_renderer_jni.cpp
    tiff_io.cpp
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(tiffrenderer_jni
    PRIVATE
        tiff
        ${log-lib}
        ${jnigraphics-lib}
)

target_compile_options(tiffrenderer_jni PRIVATE -Wall -Wextra)
