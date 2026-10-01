#pragma once
#pragma warning(disable: 4996 4267 4244)
#include <windows.h>
#include <string>
#include <functional>
#include "imgui.h"
#include "imgui_impl_win32.h"
#include "imgui_impl_opengl3.h"
#include <gl/GL.h>
#pragma comment(lib, "opengl32.lib")

inline ImVec4 colBg = ImVec4(0.059f, 0.071f, 0.094f, 1.0f);
inline ImVec4 colHeader = ImVec4(0.078f, 0.094f, 0.122f, 1.0f);
inline ImVec4 colBorder = ImVec4(0.157f, 0.180f, 0.216f, 1.0f);
inline ImVec4 colText = ImVec4(0.878f, 0.894f, 0.914f, 1.0f);
inline ImVec4 colDim = ImVec4(0.502f, 0.545f, 0.600f, 1.0f);
inline ImVec4 colAccent = ImVec4(0.345f, 0.651f, 1.0f, 1.0f);
inline ImVec4 colGreen = ImVec4(0.180f, 0.545f, 0.341f, 1.0f);
inline ImVec4 colGreenH = ImVec4(0.220f, 0.627f, 0.400f, 1.0f);
inline ImVec4 colRed = ImVec4(0.8f, 0.2f, 0.2f, 1.0f);
inline ImVec4 colBtn = ImVec4(0.118f, 0.137f, 0.169f, 1.0f);
inline ImVec4 colBtnH = ImVec4(0.157f, 0.180f, 0.220f, 1.0f);
inline ImVec4 colInput = ImVec4(0.078f, 0.094f, 0.122f, 1.0f);
inline ImVec4 colInputH = ImVec4(0.098f, 0.118f, 0.149f, 1.0f);

class GuiWindow {
public:
    bool create(const char* title, int w, int h);
    void destroy();
    bool poll();
    void beginFrame();
    void endFrame();
    HWND hwnd = nullptr;
private:
    HDC hdc = nullptr;
    HGLRC hrc = nullptr;
    WNDCLASSEXW wc = {};
    static LRESULT CALLBACK wndProc(HWND h, UINT m, WPARAM w, LPARAM l);
};

void guiStyle();
bool guiInput(const char* label, char* buf, size_t sz, const char* hint = nullptr);
bool guiInputBrowse(const char* label, char* buf, size_t sz, const char* hint, std::function<void()> fn);
bool guiCombo(const char* label, int* cur, const char* const items[], int cnt);
bool guiBtn(const char* label, const ImVec2& sz = ImVec2(0, 0), bool primary = false);
void guiProgress(float val, const ImVec2& sz = ImVec2(-1, 10));
void guiStatus(const char* txt);