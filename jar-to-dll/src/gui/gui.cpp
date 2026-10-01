#pragma warning(disable: 4996 4267 4244)
#include "gui.h"
#include "../include/JetBrains.h"
#include <dwmapi.h>
#include <vector>
#include <string>
#pragma comment(lib, "dwmapi.lib")

extern IMGUI_IMPL_API LRESULT ImGui_ImplWin32_WndProcHandler(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam);
static ImFont* g_fontMain = nullptr;

LRESULT CALLBACK GuiWindow::wndProc(HWND h, UINT m, WPARAM w, LPARAM l) {
    if (ImGui_ImplWin32_WndProcHandler(h, m, w, l)) return true;
    if (m == WM_DESTROY) { PostQuitMessage(0); return 0; }
    if (m == WM_SYSCOMMAND && (w & 0xfff0) == SC_KEYMENU) return 0;
    if (m == WM_GETMINMAXINFO) {
        LPMINMAXINFO mi = (LPMINMAXINFO)l;
        mi->ptMinTrackSize.x = 320;
        mi->ptMinTrackSize.y = 240;
        return 0;
    }
    return DefWindowProcW(h, m, w, l);
}

bool GuiWindow::create(const char* title, int w, int h) {
    HINSTANCE hInst = GetModuleHandle(0);
    wc = {sizeof(WNDCLASSEXW), CS_CLASSDC, wndProc, 0, 0, hInst, 0, 0, 0, 0, L"\x4A\x32\x44", 0};
    RegisterClassExW(&wc);
    int len = MultiByteToWideChar(CP_UTF8, 0, title, -1, 0, 0);
    std::wstring wt(len, 0);
    MultiByteToWideChar(CP_UTF8, 0, title, -1, &wt[0], len);
    int sx = GetSystemMetrics(SM_CXSCREEN);
    int sy = GetSystemMetrics(SM_CYSCREEN);
    int px = (sx - w) / 2;
    int py = (sy - h) / 2;
    hwnd = CreateWindowExW(0, wc.lpszClassName, wt.c_str(), WS_OVERLAPPEDWINDOW, px, py, w, h, 0, 0, wc.hInstance, 0);
    if (!hwnd) return false;
    PIXELFORMATDESCRIPTOR pfd = {sizeof(pfd), 1, PFD_DRAW_TO_WINDOW | PFD_SUPPORT_OPENGL | PFD_DOUBLEBUFFER, PFD_TYPE_RGBA, 32, 0,0,0,0,0,0,0,0,0,0,0,0,0, 24, 8, 0, PFD_MAIN_PLANE, 0,0,0,0};
    hdc = GetDC(hwnd);
    int pfi = ChoosePixelFormat(hdc, &pfd);
    SetPixelFormat(hdc, pfi, &pfd);
    hrc = wglCreateContext(hdc);
    wglMakeCurrent(hdc, hrc);

    BOOL dk = TRUE;
    DwmSetWindowAttribute(hwnd, 20, &dk, sizeof(dk));

    int cornerPref = 2;
    DwmSetWindowAttribute(hwnd, 33, &cornerPref, sizeof(cornerPref));

    COLORREF borderColor = RGB(88, 166, 255);
    DwmSetWindowAttribute(hwnd, 34, &borderColor, sizeof(borderColor));

    ShowWindow(hwnd, SW_SHOWDEFAULT);
    UpdateWindow(hwnd);
    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    auto& io = ImGui::GetIO();
    io.IniFilename = nullptr;
    io.ConfigFlags |= ImGuiConfigFlags_NavEnableKeyboard;
    g_fontMain = io.Fonts->AddFontFromMemoryTTF(
        (void*)JetBrains, (int)JetBrains_size,
        16.0f, nullptr, io.Fonts->GetGlyphRangesCyrillic()
    );
    if (!g_fontMain) g_fontMain = io.Fonts->AddFontDefault();
    ImGui_ImplWin32_Init(hwnd);
    ImGui_ImplOpenGL3_Init("#version 130");
    guiStyle();
    return true;
}

void GuiWindow::destroy() {
    ImGui_ImplOpenGL3_Shutdown();
    ImGui_ImplWin32_Shutdown();
    ImGui::DestroyContext();
    wglMakeCurrent(0, 0);
    wglDeleteContext(hrc);
    ReleaseDC(hwnd, hdc);
    DestroyWindow(hwnd);
    UnregisterClassW(wc.lpszClassName, wc.hInstance);
}

bool GuiWindow::poll() {
    MSG m;
    while (PeekMessage(&m, 0, 0, 0, PM_REMOVE)) {
        TranslateMessage(&m);
        DispatchMessage(&m);
        if (m.message == WM_QUIT) return false;
    }
    return true;
}

void GuiWindow::beginFrame() {
    ImGui_ImplOpenGL3_NewFrame();
    ImGui_ImplWin32_NewFrame();
    ImGui::NewFrame();
}

void GuiWindow::endFrame() {
    ImGui::Render();
    RECT r;
    GetClientRect(hwnd, &r);
    glViewport(0, 0, r.right - r.left, r.bottom - r.top);
    glClearColor(colBg.x, colBg.y, colBg.z, colBg.w);
    glClear(GL_COLOR_BUFFER_BIT);
    ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
    SwapBuffers(hdc);
}

void guiStyle() {
    auto& s = ImGui::GetStyle();
    auto* c = s.Colors;

    s.WindowRounding = 0;
    s.ChildRounding = 4.0f;
    s.FrameRounding = 4.0f;
    s.PopupRounding = 4.0f;
    s.GrabRounding = 2.0f;
    s.ScrollbarRounding = 4.0f;
    s.TabRounding = 4.0f;
    s.FramePadding = ImVec2(8, 6);
    s.ItemSpacing = ImVec2(8, 4);
    s.WindowPadding = ImVec2(12, 10);
    s.FrameBorderSize = 0;
    s.ScrollbarSize = 8;
    s.GrabMinSize = 8;
    s.WindowBorderSize = 0;
    s.PopupBorderSize = 0;

    c[ImGuiCol_WindowBg] = colBg;
    c[ImGuiCol_ChildBg] = colBg;
    c[ImGuiCol_PopupBg] = ImVec4(0.067f, 0.082f, 0.106f, 0.98f);
    c[ImGuiCol_Border] = colBorder;
    c[ImGuiCol_FrameBg] = colInput;
    c[ImGuiCol_FrameBgHovered] = colInputH;
    c[ImGuiCol_FrameBgActive] = ImVec4(0.118f, 0.137f, 0.169f, 1.0f);
    c[ImGuiCol_TitleBg] = colHeader;
    c[ImGuiCol_TitleBgActive] = colHeader;
    c[ImGuiCol_Text] = colText;
    c[ImGuiCol_TextDisabled] = colDim;
    c[ImGuiCol_Button] = colBtn;
    c[ImGuiCol_ButtonHovered] = colBtnH;
    c[ImGuiCol_ButtonActive] = ImVec4(0.2f, 0.22f, 0.27f, 1.0f);
    c[ImGuiCol_Header] = colHeader;
    c[ImGuiCol_HeaderHovered] = colBtn;
    c[ImGuiCol_HeaderActive] = colBtn;
    c[ImGuiCol_CheckMark] = colAccent;
    c[ImGuiCol_SliderGrab] = colAccent;
    c[ImGuiCol_SliderGrabActive] = ImVec4(0.45f, 0.72f, 1.0f, 1.0f);
    c[ImGuiCol_PlotHistogram] = colGreen;
    c[ImGuiCol_ScrollbarBg] = ImVec4(0.04f, 0.05f, 0.07f, 0.5f);
    c[ImGuiCol_ScrollbarGrab] = colBorder;
    c[ImGuiCol_ScrollbarGrabHovered] = colBtnH;
    c[ImGuiCol_ScrollbarGrabActive] = colAccent;
    c[ImGuiCol_Separator] = ImVec4(0.157f, 0.180f, 0.216f, 0.5f);
    c[ImGuiCol_SeparatorHovered] = colAccent;
    c[ImGuiCol_SeparatorActive] = colAccent;
}

bool guiInput(const char* label, char* buf, size_t sz, const char* hint) {
    ImGui::PushStyleColor(ImGuiCol_Text, colDim);
    ImGui::TextUnformatted(label);
    ImGui::PopStyleColor();
    ImGui::PushItemWidth(-1);
    bool r = ImGui::InputTextWithHint((std::string("##") + label).c_str(), hint ? hint : "", buf, sz);
    ImGui::PopItemWidth();
    return r;
}

bool guiInputBrowse(const char* label, char* buf, size_t sz, const char* hint, std::function<void()> fn) {
    ImGui::PushStyleColor(ImGuiCol_Text, colDim);
    ImGui::TextUnformatted(label);
    ImGui::PopStyleColor();
    float av = ImGui::GetContentRegionAvail().x;
    float fh = ImGui::GetFrameHeight();
    float bw = fh;
    float iw = av - bw - ImGui::GetStyle().ItemSpacing.x;
    ImGui::PushItemWidth(iw);
    bool r = ImGui::InputTextWithHint((std::string("##") + label).c_str(), hint ? hint : "", buf, sz);
    ImGui::PopItemWidth();
    ImGui::SameLine(0, ImGui::GetStyle().ItemSpacing.x);
    ImVec2 btnPos = ImGui::GetCursorScreenPos();
    ImGui::PushStyleVar(ImGuiStyleVar_FramePadding, ImVec2(0, 0));
    ImGui::PushStyleVar(ImGuiStyleVar_FrameRounding, 8.0f);
    if (ImGui::Button((std::string("##btn") + label).c_str(), ImVec2(bw, fh))) if (fn) fn();
    ImGui::PopStyleVar(2);
    const char* dots = "...";
    ImVec2 textSize = ImGui::CalcTextSize(dots);
    float tx = btnPos.x + (bw - textSize.x) * 0.5f;
    float ty = btnPos.y + (fh - textSize.y) * 0.5f;
    ImGui::GetWindowDrawList()->AddText(ImVec2(tx, ty), IM_COL32(255, 255, 255, 255), dots);
    return r;
}

bool guiCombo(const char* label, int* cur, const char* const items[], int cnt) {
    ImGui::PushStyleColor(ImGuiCol_Text, colDim);
    ImGui::TextUnformatted(label);
    ImGui::PopStyleColor();
    ImGui::PushItemWidth(-1);
    bool r = ImGui::Combo((std::string("##") + label).c_str(), cur, items, cnt);
    ImGui::PopItemWidth();
    return r;
}

bool guiBtn(const char* label, const ImVec2& sz, bool primary) {
    ImGui::PushStyleVar(ImGuiStyleVar_FrameRounding, 10.0f);
    if (primary) {
        ImGui::PushStyleColor(ImGuiCol_Button, colGreen);
        ImGui::PushStyleColor(ImGuiCol_ButtonHovered, colGreenH);
        ImGui::PushStyleColor(ImGuiCol_ButtonActive, ImVec4(0.14f, 0.48f, 0.26f, 1));
        ImGui::PushStyleColor(ImGuiCol_Text, ImVec4(1, 1, 1, 1));
        ImGui::PushStyleVar(ImGuiStyleVar_FrameBorderSize, 0);
    }
    bool r = ImGui::Button(label, sz);
    if (primary) {
        ImGui::PopStyleVar();
        ImGui::PopStyleColor(4);
    }
    ImGui::PopStyleVar();
    return r;
}

void guiProgress(float val, const ImVec2& sz) {
    ImGui::PushStyleColor(ImGuiCol_PlotHistogram, colAccent);
    ImGui::PushStyleColor(ImGuiCol_FrameBg, colInput);
    ImGui::PushStyleVar(ImGuiStyleVar_FrameRounding, 6);
    ImGui::PushStyleVar(ImGuiStyleVar_FrameBorderSize, 0);
    ImGui::ProgressBar(val, sz, "");
    ImGui::PopStyleVar(2);
    ImGui::PopStyleColor(2);
}

void guiStatus(const char* txt) {
    ImGui::PushStyleColor(ImGuiCol_Text, colDim);
    ImGui::TextUnformatted(txt);
    ImGui::PopStyleColor();
}