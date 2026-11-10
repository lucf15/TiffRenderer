#include <cstdio>
#include <cstdlib>
#include <new>
#include <cmath>
#include "tiff_core.h"
#include "tiff_io.h"
#include "affine.h"
static int allocations = 0;
static int budget = -1;
void* operator new(std::size_t size) {
    if (budget == 0) throw std::bad_alloc();
    if (budget > 0) --budget;
    void* p = std::malloc(size);
    if (!p) throw std::bad_alloc();
    ++allocations;
    return p;
}
void operator delete(void* p) noexcept { if (p) { --allocations; std::free(p); } }
static void verify(bool condition, const char* message) {
    if (!condition) { std::fprintf(stderr, "%s\n", message); std::exit(3); }
}
int main(int argc, char** argv) {
    const uint8_t bytes[] = {'I','I',42,0};
    const int initial = allocations;
    budget = 1;
    try { tiffrenderer::openFromMemory(bytes, sizeof(bytes)); } catch (const std::bad_alloc&) {}
    budget = -1;
    if (allocations != initial) { std::puts("allocation leak"); return 1; }
    tiffrenderer::AffineTransform matrix(1e30f,1e30f,0,1e30f,1e30f,0), inverse;
    if (matrix.invert(&inverse)) { std::puts("singular matrix accepted"); return 2; }
    verify(argc == 2, "missing fixture directory");
    char path[4096];
    std::snprintf(path, sizeof(path), "%s/single_page_rgb.tif", argv[1]);
    TiffCoreDocument* doc = nullptr;
    char error[512] = {};
    verify(tiffcore_open_path(path, &doc, error, sizeof(error)) == TIFFCORE_OK, error);
    uint32_t pixels[32 * 24] = {};
    const float identity[] = {1,0,0,0,1,0};
    verify(tiffcore_render_page(doc, 0, pixels, sizeof(pixels)-1, 32,32,24,0,0,32,24,identity,TIFFCORE_RENDER_MODE_DISPLAY,error,sizeof(error)) == TIFFCORE_ERROR_INVALID_ARG, "undersized buffer accepted");
    verify(tiffcore_render_page(doc, 0, pixels, sizeof(pixels), 32,32,24,0,0,32,24,nullptr,TIFFCORE_RENDER_MODE_DISPLAY,error,sizeof(error)) == TIFFCORE_ERROR_INVALID_ARG, "null matrix accepted");
    verify(tiffcore_set_limits(doc, 4, 64 * 1024 * 1024, error, sizeof(error)) == TIFFCORE_OK, error);
    verify(tiffcore_render_page(doc, 0, pixels, sizeof(pixels),32,32,24,0,0,32,24,identity,TIFFCORE_RENDER_MODE_DISPLAY,error,sizeof(error)) != TIFFCORE_OK, "decode budget bypassed");
    tiffcore_close(doc);
    for (int i = 0; i < 100; ++i) {
        doc = nullptr;
        verify(tiffcore_open_path(path, &doc, error, sizeof(error)) == TIFFCORE_OK, error);
        verify(tiffcore_render_page(doc, 0, pixels, sizeof(pixels),32,32,24,0,0,32,24,identity,TIFFCORE_RENDER_MODE_DISPLAY,error,sizeof(error)) == TIFFCORE_OK, error);
        tiffcore_close(doc);
    }
    std::puts("Native safety tests passed");
}
