using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Threading;
using System.Windows.Forms;

namespace KimikoLauncher
{
    static class Program
    {
        [STAThread]
        static void Main()
        {
            try
            {
                string appDir = AppDomain.CurrentDomain.BaseDirectory;
                string serverScript = Path.Combine(appDir, "server.js");

                // If not in launcher directory, check Desktop or LocalAppData
                if (!File.Exists(serverScript))
                {
                    string desktopLauncher = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Desktop), "KimikoLauncher");
                    if (File.Exists(Path.Combine(desktopLauncher, "server.js")))
                    {
                        appDir = desktopLauncher;
                        serverScript = Path.Combine(appDir, "server.js");
                    }
                }

                // Locate Node.js executable
                string nodePath = Path.Combine(appDir, "runtime", "node.exe");
                if (!File.Exists(nodePath))
                {
                    string localNode = @"C:\Program Files\nodejs\node.exe";
                    if (File.Exists(localNode))
                    {
                        nodePath = localNode;
                    }
                    else
                    {
                        nodePath = "node";
                    }
                }

                // 1. Start Node server in background
                ProcessStartInfo srvInfo = new ProcessStartInfo
                {
                    FileName = nodePath,
                    Arguments = "\"" + serverScript + "\"",
                    WorkingDirectory = appDir,
                    CreateNoWindow = true,
                    UseShellExecute = false,
                    WindowStyle = ProcessWindowStyle.Hidden
                };

                Process srvProc = Process.Start(srvInfo);

                // 2. Wait for server to respond
                int tries = 0;
                bool serverReady = false;
                while (tries < 30)
                {
                    Thread.Sleep(200);
                    try
                    {
                        HttpWebRequest req = (HttpWebRequest)WebRequest.Create("http://127.0.0.1:38250/api/info");
                        req.Timeout = 500;
                        using (HttpWebResponse resp = (HttpWebResponse)req.GetResponse())
                        {
                            if (resp.StatusCode == HttpStatusCode.OK)
                            {
                                serverReady = true;
                                break;
                            }
                        }
                    }
                    catch { }
                    tries++;
                }

                // 3. Find Browser (Edge or Chrome or default)
                string edgePath = @"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe";
                if (!File.Exists(edgePath))
                {
                    edgePath = @"C:\Program Files\Microsoft\Edge\Application\msedge.exe";
                }

                string chromePath = @"C:\Program Files\Google\Chrome\Application\chrome.exe";
                if (!File.Exists(chromePath))
                {
                    chromePath = @"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe";
                }

                string browserExe = File.Exists(edgePath) ? edgePath : (File.Exists(chromePath) ? chromePath : null);

                Process browserProc = null;
                if (browserExe != null)
                {
                    ProcessStartInfo browserInfo = new ProcessStartInfo
                    {
                        FileName = browserExe,
                        Arguments = "--app=http://127.0.0.1:38250 --window-size=1040,680 --window-position=center",
                        UseShellExecute = false
                    };
                    browserProc = Process.Start(browserInfo);
                }
                else
                {
                    Process.Start("http://127.0.0.1:38250");
                }

                if (browserProc != null)
                {
                    browserProc.WaitForExit();
                }
                else
                {
                    // Keep server alive if opened in regular browser tab
                    Thread.Sleep(5000);
                }

                // Clean up server process when closed
                try
                {
                    if (srvProc != null && !srvProc.HasExited)
                    {
                        srvProc.Kill();
                    }
                }
                catch { }
            }
            catch (Exception ex)
            {
                MessageBox.Show("Ошибка запуска Kimiko Launcher: " + ex.Message, "Kimiko Launcher", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }
    }
}
