#include <windows.h>

#pragma section(".carrier", read, write)
__declspec(allocate(".carrier")) volatile unsigned char g_carrier_reserve[0x50000] = {};

extern "C" __declspec(dllexport) unsigned int CarrierReserveSize() {
    return static_cast<unsigned int>(sizeof(g_carrier_reserve));
}

BOOL WINAPI DllMain(HINSTANCE, DWORD, LPVOID) {
    return TRUE;
}
