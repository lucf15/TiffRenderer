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
# ship with every NDK, so it's effectively free). JPEG-in-TIFF, WebP, Zstd, LERC, LZMA and JBIG
# are disabled because they'd each require vendoring another third-party codec library — a TIFF
# using one of those will fail to decode with a clear libtiff error at open time instead of
# silently misdecoding.
set(tiff-static ON CACHE BOOL "" FORCE)
set(tiff-tools OFF CACHE BOOL "" FORCE)
set(tiff-tests OFF CACHE BOOL "" FORCE)
set(tiff-contrib OFF CACHE BOOL "" FORCE)
set(tiff-docs OFF CACHE BOOL "" FORCE)
set(tiff-deprecated OFF CACHE BOOL "" FORCE)
set(tiff-install OFF CACHE BOOL "" FORCE)

set(zlib ON CACHE BOOL "" FORCE)
set(libdeflate OFF CACHE BOOL "" FORCE)
set(jpeg OFF CACHE BOOL "" FORCE)
set(old-jpeg OFF CACHE BOOL "" FORCE)
set(webp OFF CACHE BOOL "" FORCE)
set(zstd OFF CACHE BOOL "" FORCE)
set(lerc OFF CACHE BOOL "" FORCE)
set(lzma OFF CACHE BOOL "" FORCE)
set(jbig OFF CACHE BOOL "" FORCE)

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
