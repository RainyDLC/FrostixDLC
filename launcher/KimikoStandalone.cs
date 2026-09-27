using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Reflection;
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
                string localApp = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
                string targetDir = Path.Combine(localApp, "KimikoLauncher");
                string serverScript = Path.Combine(targetDir, "server.js");
                string nodePath = Path.Combine(targetDir, "runtime", "node.exe");

                // If running from an already unpacked directory containing server.js, use it directly!
                string currentDir = AppDomain.CurrentDomain.BaseDirectory;
                if (File.Exists(Path.Combine(currentDir, "server.js")) && File.Exists(Path.Combine(currentDir, "runtime", "node.exe")))
                {
                    targetDir = currentDir;
                    serverScript = Path.Combine(targetDir, "server.js");
                    nodePath = Path.Combine(targetDir, "runtime", "node.exe");
                }
                else if (!File.Exists(serverScript) || !File.Exists(nodePath))
                {
                    // Extract embedded package to %LocalAppData%\KimikoLauncher
                    Directory.CreateDirectory(targetDir);
                    Assembly asm = Assembly.GetExecutingAssembly();
                    using (Stream resStream = asm.GetManifestResourceStream("Package"))
                    {
                        if (resStream != null)
                        {
                            using (ZipArchive archive = new ZipArchive(resStream, ZipArchiveMode.Read))
                            {
                                foreach (ZipArchiveEntry entry in archive.Entries)
                                {
                                    if (string.IsNullOrEmpty(entry.Name) && (entry.FullName.EndsWith("/") || entry.FullName.EndsWith("\\")))
                                    {
                                        Directory.CreateDirectory(Path.Combine(targetDir, entry.FullName));
                                        continue;
                                    }

                                    string destPath = Path.Combine(targetDir, entry.FullName);
                                    string dir = Path.GetDirectoryName(destPath);
                                    if (!string.IsNullOrEmpty(dir))
                                    {
                                        Directory.CreateDirectory(dir);
                                    }

                                    entry.ExtractToFile(destPath, true);
                                }
                            }
                        }
                    }
                }

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
                    WorkingDirectory = targetDir,
                    CreateNoWindow = true,
                    UseShellExecute = false,
                    WindowStyle = ProcessWindowStyle.Hidden
                };

                Process srvProc = Process.Start(srvInfo);

                // 2. Wait for server to respond
                int tries = 0;
                while (tries < 40)
                {
                    Thread.Sleep(200);
                    try
                    {
                        HttpWebRequest req = (HttpWebRequest)WebRequest.Create("http://127.0.0.1:38250/api/info");
                        req.Timeout = 600;
                        using (HttpWebResponse resp = (HttpWebResponse)req.GetResponse())
                        {
                            if (resp.StatusCode == HttpStatusCode.OK)
                            {
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
