// Emscripten's linker requires a main() even when -sMODULARIZE builds an ES module callers never
// run as a program; tiffcore_module only ever gets used through its exported C functions.
int main(void) { return 0; }
