using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Reflection;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Effects;
using System.Windows.Media.Imaging;
using System.Windows.Shapes;
using System.Windows.Threading;
using SPath = System.Windows.Shapes.Path;

[assembly: AssemblyTitle("RainyDLC Launcher")]
[assembly: AssemblyDescription("RainyDLC Premium Minecraft Client Launcher")]
[assembly: AssemblyConfiguration("")]
[assembly: AssemblyCompany("RainyDLC Team")]
[assembly: AssemblyProduct("RainyDLC")]
[assembly: AssemblyCopyright("RainyDLC 2026")]
[assembly: AssemblyTrademark("")]
[assembly: AssemblyCulture("")]
[assembly: AssemblyVersion("2.1.0.0")]
[assembly: AssemblyFileVersion("2.1.0.0")]

namespace RainyDLC.Launcher
{
    public static class AppColors
    {
        public static readonly Color WindowBg = Color.FromRgb(8, 10, 15);
        public static readonly Color SurfaceBg = Color.FromRgb(13, 16, 24);
        public static readonly Color CardBg = Color.FromArgb(215, 17, 21, 32);
        public static readonly Color CardHover = Color.FromArgb(240, 22, 28, 42);
        public static readonly Color BorderSubtle = Color.FromArgb(160, 36, 44, 66);
        public static readonly Color BorderHover = Color.FromArgb(220, 60, 75, 110);

        public static readonly Color AccentPrimary = Color.FromRgb(30, 64, 175);   // Dark Blue
        public static readonly Color AccentHover   = Color.FromRgb(29, 78, 216);   // Dark Blue Hover
        public static readonly Color AccentLight   = Color.FromRgb(37, 99, 235);   // Blue Accent
        public static readonly Color AccentDark    = Color.FromRgb(30, 58, 138);   // Deep Navy
        public static readonly Color AccentCyan    = Color.FromRgb(30, 64, 175);   // Dark Blue
        public static readonly Color AccentGreen   = Color.FromRgb(16, 185, 129);  // Emerald
        public static readonly Color AccentRed     = Color.FromRgb(185, 28, 28);   // Dark Red
        public static readonly Color AccentAmber   = Color.FromRgb(217, 119, 6);   // Amber

        public static readonly Color TextPrimary = Color.FromRgb(248, 250, 252);
        public static readonly Color TextSecondary = Color.FromRgb(156, 163, 175);
        public static readonly Color TextMuted = Color.FromRgb(100, 116, 139);

        public static readonly Color InputBg = Color.FromRgb(11, 14, 22);
        public static readonly Color InputBorder = Color.FromRgb(32, 40, 60);
    }

    public static class SvgIcons
    {
        public const string Play = "M8 5v14l11-7z";
        public const string Stop = "M6 6h12v12H6z";
        public const string Home = "M3 12l9-9 9 9M5 10v10a1 1 0 001 1h4a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1h4a1 1 0 001-1V10";
        public const string Mods = "M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5";
        public const string Settings = "M4 21v-7m0-4V3m8 21v-9m0-4V3m8 21v-5m0-4V3M1 14h6m2-6h6m2 8h6";
        public const string Terminal = "M4 17l6-6-6-6m8 14h8";
        public const string Folder = "M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z";
        public const string Download = "M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M7 10l5 5 5-5M12 15V3";
        public const string Search = "M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z";
        public const string Refresh = "M23 4v6h-6M1 20v-6h6M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15";
        public const string Close = "M18 6L6 18M6 6l12 12";
        public const string Minimize = "M5 12h14";
        public const string Check = "M20 6L9 17l-5-5";
        public const string User = "M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2M12 3a4 4 0 100 8 4 4 0 000-8z";
        public const string Clock = "M12 2a10 10 0 100 20 10 10 0 000-20zM12 6v6l4 2";
        public const string Chip = "M4 4h16v16H4zM9 9h6v6H9zM9 1v3M15 1v3M9 20v3M15 20v3M1 9h3M1 15h3M20 9h3M20 15h3";
        public const string Copy = "M8 4v12a2 2 0 002 2h8a2 2 0 002-2V7.242a2 2 0 00-.602-1.43L16.083 2.57A2 2 0 0014.685 2H10a2 2 0 00-2 2zM16 18v2a2 2 0 01-2 2H6a2 2 0 01-2-2V8a2 2 0 012-2h2";
        public const string Trash = "M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2";
        public const string Rocket = "M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 00-2.91-.09zM12 15l-3-3m1.5-1.5C11.5 9.5 14 7 17 4c1.5-1.5 3-1.5 3-1.5s0 1.5-1.5 3c-3 3-5.5 5.5-6.5 6.5z";
        public const string Sparkles = "M12 2l2.4 7.2L22 12l-7.6 2.8L12 22l-2.4-7.2L2 12l7.6-2.8z";
        public const string Cube = "M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16zM3.27 6.96L12 12.01l8.73-5.05M12 22.08V12";
        public const string Target = "M12 2a10 10 0 100 20 10 10 0 000-20zm0 4a6 6 0 100 12 6 6 0 000-12zm0 4a2 2 0 100 4 2 2 0 000-4z";
    }

    public static class IconHelper
    {
        public static SPath CreateStrokeIcon(string svgPath, double size, Brush stroke, double thickness = 1.7)
        {
            return new SPath
            {
                Data = Geometry.Parse(svgPath),
                Stroke = stroke,
                StrokeThickness = thickness,
                StrokeStartLineCap = PenLineCap.Round,
                StrokeEndLineCap = PenLineCap.Round,
                StrokeLineJoin = PenLineJoin.Round,
                Fill = Brushes.Transparent,
                Width = size,
                Height = size,
                Stretch = Stretch.Uniform,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
        }

        public static SPath CreateFillIcon(string svgPath, double size, Brush fill)
        {
            return new SPath
            {
                Data = Geometry.Parse(svgPath),
                Fill = fill,
                Width = size,
                Height = size,
                Stretch = Stretch.Uniform,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
        }
    }

    public class LauncherData
    {
        public string Username = "Owner";
        public string Email = "";
        public string Role = "Player";
        public int UserId = 6038;
        public string RegDate = "2026";
        public int LaunchCount = 0;
        public int PlaytimeMinutes = 0;
        public string LastLaunchTime = "";
        public int RamMb = 4096;
        public string JvmArgs = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200";
        public string CustomProjectPath = "";
        public string ClientDownloadUrl = "https://github.com/RainyDLC/FrostixDLC/releases/latest/download/rainydlc.jar";
        public string CustomGamePath = "";
        public int LaunchMode = 0; // 0=Auto, 1=Gradlew, 2=Minecraft
        public Dictionary<string, bool> ModStates = new Dictionary<string, bool>();

        public static string FormatPlaytime(int totalMinutes)
        {
            if (totalMinutes <= 0) return "0 мин";
            int hours = totalMinutes / 60;
            int mins = totalMinutes % 60;
            if (hours > 0)
            {
                return hours + " ч " + mins + " мин";
            }
            return mins + " мин";
        }

        public static string DataFilePath
        {
            get
            {
                string dir = System.IO.Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                    "RainyDLC"
                );
                if (!Directory.Exists(dir)) Directory.CreateDirectory(dir);
                return System.IO.Path.Combine(dir, "launcher_state.txt");
            }
        }

        public static LauncherData Load()
        {
            LauncherData d = new LauncherData();
            try
            {
                string file = DataFilePath;
                if (File.Exists(file))
                {
                    string[] lines = File.ReadAllLines(file);
                    foreach (string line in lines)
                    {
                        if (string.IsNullOrEmpty(line) || !line.Contains("=")) continue;
                        int idx = line.IndexOf('=');
                        string key = line.Substring(0, idx).Trim();
                        string val = line.Substring(idx + 1).Trim();

                        if (key == "Username") d.Username = string.IsNullOrEmpty(val) ? "Owner" : val;
                        else if (key == "Email") d.Email = val;
                        else if (key == "Role") d.Role = val;
                        else if (key == "LaunchCount") int.TryParse(val, out d.LaunchCount);
                        else if (key == "PlaytimeMinutes") int.TryParse(val, out d.PlaytimeMinutes);
                        else if (key == "LastLaunchTime") d.LastLaunchTime = val;
                        else if (key == "RamMb") int.TryParse(val, out d.RamMb);
                        else if (key == "JvmArgs") d.JvmArgs = val;
                        else if (key == "CustomProjectPath") d.CustomProjectPath = val;
                        else if (key == "ClientDownloadUrl") d.ClientDownloadUrl = val;
                        else if (key == "CustomGamePath") d.CustomGamePath = val;
                        else if (key == "LaunchMode") int.TryParse(val, out d.LaunchMode);
                        else if (key.StartsWith("Mod_"))
                        {
                            string mod = key.Substring(4);
                            bool st;
                            if (bool.TryParse(val, out st)) d.ModStates[mod] = st;
                        }
                    }
                }
            }
            catch { }

            if (d.RamMb < 1024) d.RamMb = 4096;

            if (string.IsNullOrEmpty(d.LastLaunchTime))
            {
                try
                {
                    string runLog = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "run", "logs", "latest.log");
                    if (File.Exists(runLog))
                    {
                        DateTime dt = File.GetLastWriteTime(runLog);
                        d.LastLaunchTime = dt.ToString("dd MMMM в HH:mm", new System.Globalization.CultureInfo("ru-RU"));
                        if (d.LaunchCount == 0) d.LaunchCount = 1;
                    }
                    else
                    {
                        d.LastLaunchTime = "Еще не запускался";
                    }
                }
                catch
                {
                    d.LastLaunchTime = "Еще не запускался";
                }
            }

            return d;
        }

        public void Save()
        {
            try
            {
                StringBuilder sb = new StringBuilder();
                sb.AppendLine("Username=" + Username);
                sb.AppendLine("Email=" + Email);
                sb.AppendLine("Role=" + Role);
                sb.AppendLine("LaunchCount=" + LaunchCount);
                sb.AppendLine("PlaytimeMinutes=" + PlaytimeMinutes);
                sb.AppendLine("LastLaunchTime=" + LastLaunchTime);
                sb.AppendLine("RamMb=" + RamMb);
                sb.AppendLine("JvmArgs=" + JvmArgs);
                sb.AppendLine("CustomProjectPath=" + CustomProjectPath);
                sb.AppendLine("ClientDownloadUrl=" + ClientDownloadUrl);
                sb.AppendLine("CustomGamePath=" + CustomGamePath);
                sb.AppendLine("LaunchMode=" + LaunchMode);
                foreach (KeyValuePair<string, bool> kv in ModStates)
                {
                    sb.AppendLine("Mod_" + kv.Key + "=" + kv.Value);
                }
                File.WriteAllText(DataFilePath, sb.ToString());
            }
            catch { }
        }
    }

    public class ModrinthItem
    {
        public string Title = "";
        public string Slug = "";
        public string Description = "";
        public string IconUrl = "";
        public string Author = "";
        public int Downloads = 0;

        public string FormattedDownloads
        {
            get
            {
                if (Downloads >= 1000000) return (Downloads / 1000000.0).ToString("0.#") + "M";
                if (Downloads >= 1000) return (Downloads / 1000.0).ToString("0.#") + "k";
                return Downloads.ToString();
            }
        }
    }

    public class ToggleSwitch : Border
    {
        private bool _isChecked = true;
        private Border _thumb;

        public event Action<bool> CheckedChanged;

        public bool IsChecked
        {
            get { return _isChecked; }
            set
            {
                if (_isChecked != value)
                {
                    _isChecked = value;
                    UpdateVisual(true);
                }
            }
        }

        public ToggleSwitch(bool initialState = true)
        {
            _isChecked = initialState;
            Width = 36;
            Height = 20;
            CornerRadius = new CornerRadius(10);
            Cursor = Cursors.Hand;
            ClipToBounds = true;
            BorderThickness = new Thickness(1);

            UpdateColors();

            _thumb = new Border
            {
                Width = 14,
                Height = 14,
                CornerRadius = new CornerRadius(7),
                Background = Brushes.White,
                VerticalAlignment = VerticalAlignment.Center,
                HorizontalAlignment = HorizontalAlignment.Left,
                Margin = new Thickness(_isChecked ? 18 : 2, 0, 0, 0),
                Effect = new DropShadowEffect
                {
                    Color = Colors.Black,
                    BlurRadius = 3,
                    ShadowDepth = 1,
                    Opacity = 0.25
                }
            };
            Child = _thumb;

            MouseLeftButtonDown += (s, e) =>
            {
                _isChecked = !_isChecked;
                UpdateVisual(true);
                if (CheckedChanged != null) CheckedChanged(_isChecked);
            };
        }

        private void UpdateColors()
        {
            Background = new SolidColorBrush(_isChecked ? AppColors.AccentPrimary : Color.FromRgb(24, 30, 44));
            BorderBrush = new SolidColorBrush(_isChecked ? AppColors.AccentLight : Color.FromRgb(36, 44, 64));
        }

        private void UpdateVisual(bool animate = false)
        {
            Thickness targetMargin = new Thickness(_isChecked ? 18 : 2, 0, 0, 0);

            if (animate)
            {
                ThicknessAnimation thumbAnim = new ThicknessAnimation
                {
                    To = targetMargin,
                    Duration = TimeSpan.FromMilliseconds(160),
                    EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
                };
                _thumb.BeginAnimation(Border.MarginProperty, thumbAnim);
                UpdateColors();
            }
            else
            {
                _thumb.BeginAnimation(Border.MarginProperty, null);
                _thumb.Margin = targetMargin;
                UpdateColors();
            }
        }
    }

    public class MainWindow : Window
    {
        private LauncherData _data;
        private string _projectDir;
        private Process _runningProcess;
        private DispatcherTimer _playtimeTimer;
        private DateTime _sessionStart;

        // Top Navigation Tabs
        private List<Border> _navPills = new List<Border>();
        private List<TextBlock> _navTexts = new List<TextBlock>();
        private List<SPath> _navIcons = new List<SPath>();
        private Grid _pageHome;
        private Grid _pageMods;
        private Grid _pageSettings;
        private Grid _pageConsole;
        private int _currentPageIndex = -1;

        // Bottom Dock Controls
        private Button _btnDockLaunch;
        private TextBlock _btnDockLaunchText;
        private SPath _btnDockLaunchIcon;
        private TextBlock _txtDockUser;
        private Ellipse _dockStatusDot;
        private TextBlock _txtDockStatus;
        private TextBlock _txtDockRamBadge;

        // Home View Controls
        private TextBlock _txtHeroPlaytime;
        private TextBlock _txtHeroLaunches;
        private TextBlock _txtHeroLastLaunch;
        private TextBlock _txtHeroModsCount;
        private TextBlock _txtHeroClientStatus;
        private Button _btnHeroDownload;
        private TextBlock _txtHeroUsernameDisplay;
        private TextBlock _txtHeroRamDisplay;
        private List<Border> _homeRamPills = new List<Border>();
        private List<TextBlock> _homeRamPillTexts = new List<TextBlock>();

        // Client Download Controls
        private Border _downloadBanner;
        private TextBlock _txtDownloadTitle;
        private TextBlock _txtDownloadStatus;
        private TextBlock _txtDownloadDetails;
        private ProgressBar _downloadProgressBar;
        private Button _btnCancelDownload;
        private WebClient _activeDownloader = null;
        private bool _isDownloading = false;
        private bool _autoLaunchAfterDownload = false;
        private DateTime _downloadStartTime;

        // Settings View Controls (macOS / Discord grouped cards)
        private TextBox _txtSettingsUser;
        private TextBox _txtSettingsRamMb;
        private TextBox _txtSettingsJvm;
        private TextBlock _txtSettingsPath;
        private TextBox _txtSettingsDownloadUrl;
        private TextBlock _txtSettingsClientStatus;
        private Button _btnSettingsDownloadClient;
        private List<Border> _ramPillBoxes = new List<Border>();
        private List<TextBlock> _ramPillTexts = new List<TextBlock>();

        // Console Log Box
        private TextBox _txtConsoleLogs;
        private ScrollViewer _scrollConsole;
        private TextBlock _txtConsoleHeaderStatus;

        // Mods View References
        private Border _tabLocalBtn;
        private TextBlock _tabLocalText;
        private Border _tabModrinthBtn;
        private TextBlock _tabModrinthText;
        private Grid _modsLocalView;
        private Grid _modsModrinthView;
        private StackPanel _localModsStack;
        private StackPanel _modrinthCardsLeft;
        private StackPanel _modrinthCardsRight;
        private TextBox _txtModrinthSearch;
        private TextBlock _txtModrinthStatus;
        private bool _modrinthLoadedOnce = false;

        public MainWindow()
        {
            _data = LauncherData.Load();
            _projectDir = ResolveProjectDir();

            InitializeWindow();
            BuildUI();
            UpdateRamDisplays();
        }

        private void InitializeWindow()
        {
            Title = "RainyDLC";
            Width = 1040;
            Height = 650;
            WindowStartupLocation = WindowStartupLocation.CenterScreen;
            WindowStyle = WindowStyle.None;
            AllowsTransparency = true;
            Background = Brushes.Transparent;
            ResizeMode = ResizeMode.CanMinimize;

            Loaded += (s, e) =>
            {
                DoubleAnimation winFade = new DoubleAnimation
                {
                    From = 0.0,
                    To = 1.0,
                    Duration = TimeSpan.FromMilliseconds(220),
                    EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
                };
                BeginAnimation(UIElement.OpacityProperty, winFade);
            };

            try
            {
                string iconPath = System.IO.Path.Combine(_projectDir, "rainydlc.ico");
                if (File.Exists(iconPath))
                {
                    Icon = BitmapFrame.Create(new Uri(iconPath, UriKind.Absolute));
                }
            }
            catch { }
        }

        private void BuildUI()
        {
            Border root = new Border
            {
                CornerRadius = new CornerRadius(18),
                Background = new SolidColorBrush(AppColors.WindowBg),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(AppColors.BorderSubtle),
                Effect = new DropShadowEffect
                {
                    Color = Colors.Black,
                    BlurRadius = 40,
                    ShadowDepth = 4,
                    Opacity = 0.8
                }
            };

            Grid rootGrid = new Grid { ClipToBounds = true };

            // 1. Subtle Atmospheric Backdrop Art
            Image bgArt = new Image
            {
                Stretch = Stretch.UniformToFill,
                Opacity = 0.65,
                IsHitTestVisible = false
            };
            string bgFile = System.IO.Path.Combine(_projectDir, "launcher_bg.jpg");
            if (!File.Exists(bgFile))
            {
                bgFile = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "launcher_bg.jpg");
            }
            if (File.Exists(bgFile))
            {
                try
                {
                    BitmapImage bmp = new BitmapImage();
                    bmp.BeginInit();
                    bmp.UriSource = new Uri(bgFile, UriKind.Absolute);
                    bmp.CacheOption = BitmapCacheOption.OnLoad;
                    bmp.EndInit();
                    bgArt.Source = bmp;
                }
                catch { }
            }
            rootGrid.Children.Add(bgArt);

            // 2. Cinematic Atmospheric Gradient Overlay
            LinearGradientBrush gradientOverlay = new LinearGradientBrush
            {
                StartPoint = new Point(0, 0),
                EndPoint = new Point(0, 1)
            };
            gradientOverlay.GradientStops.Add(new GradientStop(Color.FromArgb(170, 7, 9, 15), 0.0));
            gradientOverlay.GradientStops.Add(new GradientStop(Color.FromArgb(50, 7, 9, 15), 0.35));
            gradientOverlay.GradientStops.Add(new GradientStop(Color.FromArgb(95, 7, 9, 15), 0.70));
            gradientOverlay.GradientStops.Add(new GradientStop(Color.FromArgb(235, 6, 8, 14), 1.0));
            rootGrid.Children.Add(new Border { Background = gradientOverlay, IsHitTestVisible = false });

            // 3. Main Layout
            Grid mainLayout = new Grid();
            mainLayout.RowDefinitions.Add(new RowDefinition { Height = new GridLength(52, GridUnitType.Pixel) }); // Header & Tabs
            mainLayout.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });   // Pages Area
            mainLayout.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });                         // Bottom Dock

            // Top Header & Navigation Bar
            Border headerBar = CreateTopHeader();
            Grid.SetRow(headerBar, 0);
            mainLayout.Children.Add(headerBar);

            // Pages Container
            Grid contentArea = new Grid { Margin = new Thickness(24, 16, 24, 16) };

            _pageHome = CreatePageHome();
            _pageMods = CreatePageMods();
            _pageSettings = CreatePageSettings();
            _pageConsole = CreatePageConsole();

            contentArea.Children.Add(_pageHome);
            contentArea.Children.Add(_pageMods);
            contentArea.Children.Add(_pageSettings);
            contentArea.Children.Add(_pageConsole);

            Grid.SetRow(contentArea, 1);
            mainLayout.Children.Add(contentArea);

            // Bottom Dock
            Border launchDock = CreateBottomLaunchDock();
            Grid.SetRow(launchDock, 2);
            mainLayout.Children.Add(launchDock);

            rootGrid.Children.Add(mainLayout);
            root.Child = rootGrid;
            Content = root;

            SwitchPage(0);
            UpdateClientStatusUI();
        }

        #region Top Header & Centered Navigation

        private Border CreateTopHeader()
        {
            Border bar = new Border
            {
                Height = 52,
                Background = new SolidColorBrush(Color.FromArgb(150, 8, 11, 17)),
                BorderThickness = new Thickness(0, 0, 0, 1),
                BorderBrush = new SolidColorBrush(Color.FromArgb(40, 255, 255, 255)),
                Padding = new Thickness(20, 0, 12, 0),
                CornerRadius = new CornerRadius(14, 14, 0, 0)
            };

            bar.MouseLeftButtonDown += (s, e) =>
            {
                if (e.ButtonState == MouseButtonState.Pressed) DragMove();
            };

            Grid g = new Grid();
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });                         // Brand Logo
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });   // Center Nav Tabs
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });                         // Window Controls

            // 1. Brand Logo
            StackPanel brand = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                VerticalAlignment = VerticalAlignment.Center
            };

            Border logoPill = new Border
            {
                Width = 26,
                Height = 26,
                CornerRadius = new CornerRadius(6),
                Background = new SolidColorBrush(AppColors.AccentPrimary)
            };
            logoPill.Child = IconHelper.CreateStrokeIcon(SvgIcons.Sparkles, 14, Brushes.White, 1.8);
            brand.Children.Add(logoPill);

            TextBlock brandTitle = new TextBlock
            {
                Text = "RainyDLC",
                FontSize = 14.5,
                FontWeight = FontWeights.Bold,
                Foreground = Brushes.White,
                Margin = new Thickness(10, 0, 8, 0),
                VerticalAlignment = VerticalAlignment.Center
            };
            brand.Children.Add(brandTitle);

            Border verTag = new Border
            {
                Background = new SolidColorBrush(Color.FromArgb(45, 30, 64, 175)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromArgb(90, 30, 64, 175)),
                CornerRadius = new CornerRadius(4),
                Padding = new Thickness(6, 1.5, 6, 1.5),
                VerticalAlignment = VerticalAlignment.Center
            };
            verTag.Child = new TextBlock
            {
                Text = "1.21.11",
                FontSize = 9.5,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(AppColors.AccentLight)
            };
            brand.Children.Add(verTag);

            Grid.SetColumn(brand, 0);
            g.Children.Add(brand);

            // 2. Centered Navigation Segmented Bar
            Border navContainer = new Border
            {
                Background = new SolidColorBrush(Color.FromArgb(130, 12, 16, 26)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromArgb(35, 255, 255, 255)),
                CornerRadius = new CornerRadius(8),
                Padding = new Thickness(3),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };

            StackPanel navStack = new StackPanel { Orientation = Orientation.Horizontal };

            navStack.Children.Add(CreateTopNavButton(SvgIcons.Home, "Главная", 0));
            navStack.Children.Add(CreateTopNavButton(SvgIcons.Mods, "Моды", 1));
            navStack.Children.Add(CreateTopNavButton(SvgIcons.Settings, "Настройки", 2));
            navStack.Children.Add(CreateTopNavButton(SvgIcons.Terminal, "Консоль", 3));

            navContainer.Child = navStack;
            Grid.SetColumn(navContainer, 1);
            g.Children.Add(navContainer);

            // 3. Window Controls (Minimize, Close)
            StackPanel winControls = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                VerticalAlignment = VerticalAlignment.Center
            };

            Button btnMin = CreateTitleIconButton(SvgIcons.Minimize, (s, e) => WindowState = WindowState.Minimized, false);
            Button btnClose = CreateTitleIconButton(SvgIcons.Close, (s, e) => CloseWindow(), true);

            winControls.Children.Add(btnMin);
            winControls.Children.Add(btnClose);

            Grid.SetColumn(winControls, 2);
            g.Children.Add(winControls);

            bar.Child = g;
            return bar;
        }

        private UIElement CreateTopNavButton(string svgPath, string label, int pageIndex)
        {
            Border pill = new Border
            {
                Height = 30,
                CornerRadius = new CornerRadius(6),
                Background = Brushes.Transparent,
                Padding = new Thickness(14, 0, 14, 0),
                Margin = new Thickness(2, 0, 2, 0),
                Cursor = Cursors.Hand
            };

            StackPanel sp = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                VerticalAlignment = VerticalAlignment.Center
            };

            SPath icon = IconHelper.CreateStrokeIcon(svgPath, 14, new SolidColorBrush(AppColors.TextMuted), 1.8);
            sp.Children.Add(icon);

            TextBlock tb = new TextBlock
            {
                Text = label,
                FontSize = 12,
                FontWeight = FontWeights.Medium,
                Foreground = new SolidColorBrush(AppColors.TextSecondary),
                Margin = new Thickness(8, 0, 0, 0),
                VerticalAlignment = VerticalAlignment.Center
            };
            sp.Children.Add(tb);

            pill.Child = sp;

            _navPills.Add(pill);
            _navTexts.Add(tb);
            _navIcons.Add(icon);

            pill.MouseEnter += (s, e) =>
            {
                if (_currentPageIndex != pageIndex)
                {
                    pill.Background = new SolidColorBrush(Color.FromArgb(120, 26, 32, 48));
                    tb.Foreground = Brushes.White;
                }
            };
            pill.MouseLeave += (s, e) =>
            {
                if (_currentPageIndex != pageIndex)
                {
                    pill.Background = Brushes.Transparent;
                    tb.Foreground = new SolidColorBrush(AppColors.TextSecondary);
                }
            };
            pill.MouseLeftButtonDown += (s, e) => SwitchPage(pageIndex);

            return pill;
        }

        private Button CreateTitleIconButton(string svgPath, RoutedEventHandler onClick, bool isClose)
        {
            Button btn = new Button
            {
                Width = 30,
                Height = 28,
                Margin = new Thickness(4, 0, 0, 0),
                Cursor = Cursors.Hand,
                Focusable = false
            };

            SPath p = IconHelper.CreateStrokeIcon(svgPath, 11, new SolidColorBrush(AppColors.TextMuted), 1.8);
            btn.Content = p;

            ControlTemplate tpl = new ControlTemplate(typeof(Button));
            FrameworkElementFactory bdr = new FrameworkElementFactory(typeof(Border));
            bdr.Name = "Bdr";
            bdr.SetValue(Border.CornerRadiusProperty, new CornerRadius(6));
            bdr.SetValue(Border.BackgroundProperty, Brushes.Transparent);

            FrameworkElementFactory cp = new FrameworkElementFactory(typeof(ContentPresenter));
            cp.SetValue(ContentPresenter.HorizontalAlignmentProperty, HorizontalAlignment.Center);
            cp.SetValue(ContentPresenter.VerticalAlignmentProperty, VerticalAlignment.Center);
            bdr.AppendChild(cp);

            tpl.VisualTree = bdr;

            Trigger hov = new Trigger { Property = Button.IsMouseOverProperty, Value = true };
            if (isClose)
            {
                hov.Setters.Add(new Setter(Border.BackgroundProperty, new SolidColorBrush(AppColors.AccentRed), "Bdr"));
            }
            else
            {
                hov.Setters.Add(new Setter(Border.BackgroundProperty, new SolidColorBrush(Color.FromRgb(26, 32, 46)), "Bdr"));
            }
            tpl.Triggers.Add(hov);

            btn.Template = tpl;
            btn.Click += onClick;

            btn.MouseEnter += (s, e) => p.Stroke = Brushes.White;
            btn.MouseLeave += (s, e) => p.Stroke = new SolidColorBrush(AppColors.TextMuted);

            return btn;
        }

        private void SwitchPage(int index)
        {
            if (_currentPageIndex == index) return;
            _currentPageIndex = index;

            Grid[] pages = new Grid[] { _pageHome, _pageMods, _pageSettings, _pageConsole };
            for (int i = 0; i < pages.Length; i++)
            {
                Grid p = pages[i];
                if (p == null) continue;

                if (i == index)
                {
                    p.Visibility = Visibility.Visible;

                    TranslateTransform tt = p.RenderTransform as TranslateTransform;
                    if (tt == null)
                    {
                        tt = new TranslateTransform();
                        p.RenderTransform = tt;
                    }

                    DoubleAnimation fadeAnim = new DoubleAnimation
                    {
                        From = 0.0,
                        To = 1.0,
                        Duration = TimeSpan.FromMilliseconds(180),
                        EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
                    };
                    p.BeginAnimation(UIElement.OpacityProperty, fadeAnim);

                    DoubleAnimation slideAnim = new DoubleAnimation
                    {
                        From = 8.0,
                        To = 0.0,
                        Duration = TimeSpan.FromMilliseconds(180),
                        EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
                    };
                    tt.BeginAnimation(TranslateTransform.YProperty, slideAnim);
                }
                else
                {
                    p.Visibility = Visibility.Collapsed;
                }
            }

            for (int i = 0; i < _navPills.Count; i++)
            {
                Border pill = _navPills[i];
                TextBlock txt = _navTexts[i];
                SPath icon = _navIcons[i];

                if (i == index)
                {
                    pill.Background = new SolidColorBrush(Color.FromArgb(200, 32, 40, 60));
                    pill.BorderBrush = new SolidColorBrush(AppColors.BorderHover);
                    pill.BorderThickness = new Thickness(1);
                    txt.Foreground = Brushes.White;
                    txt.FontWeight = FontWeights.SemiBold;
                    icon.Stroke = new SolidColorBrush(AppColors.AccentLight);
                }
                else
                {
                    pill.Background = Brushes.Transparent;
                    pill.BorderBrush = Brushes.Transparent;
                    pill.BorderThickness = new Thickness(0);
                    txt.Foreground = new SolidColorBrush(AppColors.TextSecondary);
                    txt.FontWeight = FontWeights.Medium;
                    icon.Stroke = new SolidColorBrush(AppColors.TextMuted);
                }
            }
        }

        #endregion

        #region Page 0: Главная (Showcase & Spotlight)

        private Grid CreatePageHome()
        {
            Grid g = new Grid();
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });                         // 0: Client Hero Title
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });                         // 1: Download Banner (Collapsible)
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(14, GridUnitType.Pixel) }); // 2: Gap
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });   // 3: 2-Column Hub

            // 1. Client Hero Branding (Clean, open, cinematic)
            Grid heroGrid = new Grid { Margin = new Thickness(4, 4, 4, 0) };
            heroGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            heroGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            // Hero Left
            StackPanel heroLeft = new StackPanel { VerticalAlignment = VerticalAlignment.Center };

            StackPanel titleRow = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
            TextBlock brandTitle = new TextBlock
            {
                Text = "RainyDLC Client",
                FontSize = 28,
                FontWeight = FontWeights.Bold,
                Foreground = Brushes.White,
                VerticalAlignment = VerticalAlignment.Center
            };
            titleRow.Children.Add(brandTitle);

            Border verCapsule = new Border
            {
                Background = new SolidColorBrush(Color.FromArgb(40, 30, 64, 175)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromArgb(90, 30, 64, 175)),
                CornerRadius = new CornerRadius(4),
                Padding = new Thickness(7, 2, 7, 2),
                Margin = new Thickness(10, 0, 0, 0),
                VerticalAlignment = VerticalAlignment.Center
            };
            verCapsule.Child = new TextBlock
            {
                Text = "1.21.11",
                FontSize = 10,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(AppColors.AccentLight)
            };
            titleRow.Children.Add(verCapsule);
            heroLeft.Children.Add(titleRow);

            heroLeft.Children.Add(new TextBlock
            {
                Text = "Minecraft 1.21.11 • Fabric Client • Встроенная оптимизация FPS",
                FontSize = 13,
                Foreground = new SolidColorBrush(Color.FromRgb(203, 213, 225)),
                Margin = new Thickness(0, 4, 0, 10)
            });

            // Feature Badges Row
            StackPanel tagsRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 10) };
            tagsRow.Children.Add(CreateMinimalTag("Fabric Loader"));
            tagsRow.Children.Add(CreateMinimalTag("Sodium Engine"));
            tagsRow.Children.Add(CreateMinimalTag("Target ESP"));
            tagsRow.Children.Add(CreateMinimalTag("Ghost Modules"));
            heroLeft.Children.Add(tagsRow);

            // Client Status Row
            StackPanel statusRow = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };

            _txtHeroClientStatus = new TextBlock
            {
                Text = "Проверка файлов...",
                FontSize = 12,
                FontWeight = FontWeights.Medium,
                Foreground = new SolidColorBrush(AppColors.TextSecondary),
                VerticalAlignment = VerticalAlignment.Center
            };
            statusRow.Children.Add(_txtHeroClientStatus);

            _btnHeroDownload = CreateCompactButton(SvgIcons.Download, "Скачать клиент", (s, e) =>
            {
                StartClientDownload(false);
            });
            _btnHeroDownload.Margin = new Thickness(12, 0, 0, 0);
            statusRow.Children.Add(_btnHeroDownload);

            heroLeft.Children.Add(statusRow);
            Grid.SetColumn(heroLeft, 0);
            heroGrid.Children.Add(heroLeft);

            // Hero Right: Clean Playtime Pill
            Border sessionPill = new Border
            {
                Background = new SolidColorBrush(Color.FromArgb(140, 12, 16, 26)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromArgb(40, 255, 255, 255)),
                CornerRadius = new CornerRadius(8),
                Padding = new Thickness(14, 10, 14, 10),
                VerticalAlignment = VerticalAlignment.Center
            };

            StackPanel spSession = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
            spSession.Children.Add(IconHelper.CreateStrokeIcon(SvgIcons.Clock, 14, new SolidColorBrush(AppColors.TextMuted), 1.8));

            StackPanel spTimeTexts = new StackPanel { Margin = new Thickness(8, 0, 0, 0), VerticalAlignment = VerticalAlignment.Center };
            _txtHeroPlaytime = new TextBlock
            {
                Text = LauncherData.FormatPlaytime(_data.PlaytimeMinutes),
                FontSize = 14,
                FontWeight = FontWeights.Bold,
                Foreground = Brushes.White
            };
            spTimeTexts.Children.Add(_txtHeroPlaytime);
            spTimeTexts.Children.Add(new TextBlock
            {
                Text = "Игровое время",
                FontSize = 10,
                Foreground = new SolidColorBrush(AppColors.TextMuted),
                Margin = new Thickness(0, 1, 0, 0)
            });
            spSession.Children.Add(spTimeTexts);
            sessionPill.Child = spSession;

            // Retain references for background updates
            _txtHeroLaunches = new TextBlock { Text = _data.LaunchCount + " раз" };
            _txtHeroLastLaunch = new TextBlock { Text = _data.LastLaunchTime };
            _txtHeroUsernameDisplay = new TextBlock { Text = _data.Username };
            _txtHeroRamDisplay = new TextBlock { Text = (_data.RamMb / 1024) + " ГБ ОЗУ" };

            Grid.SetColumn(sessionPill, 1);
            heroGrid.Children.Add(sessionPill);

            Grid.SetRow(heroGrid, 0);
            g.Children.Add(heroGrid);

            // 1.5 Download Banner (Collapsible)
            _downloadBanner = CreateDownloadBanner();
            Grid.SetRow(_downloadBanner, 1);
            g.Children.Add(_downloadBanner);

            // 2. Main 2-Column Hub (High-Utility, Balanced)
            Grid hubGrid = new Grid();
            hubGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1.15, GridUnitType.Star) }); // Left: Features
            hubGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(14, GridUnitType.Pixel) });   // Gap
            hubGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(0.85, GridUnitType.Star) }); // Right: Quick Hub

            // Left Section: Feature Spotlight Card
            Border panelFeatures = CreateHomeSectionCard();
            StackPanel spFeat = new StackPanel();

            spFeat.Children.Add(CreateHomeCardHeader(SvgIcons.Sparkles, "ОБНОВЛЕНИЕ КЛИЕНТА • ВЕРСИЯ 1.21.11"));

            StackPanel featList = new StackPanel { Margin = new Thickness(0, 12, 0, 0) };

            featList.Children.Add(CreateFeatureItem(
                SvgIcons.Target,
                "Target ESP «Череп»",
                "Динамический череп над целью с визуализацией трещин от полученного урона."
            ));

            featList.Children.Add(CreateFeatureItem(
                SvgIcons.Rocket,
                "Движок Sodium & Iris",
                "Высокий FPS, плавная синхронизация кадров и полная поддержка современных шейдеров."
            ));

            featList.Children.Add(CreateFeatureItem(
                SvgIcons.Cube,
                "Модули Fabric & Защита",
                "Встроенный модуль AutoLeave, визуальные настройки и поддержка любых сторонних модов."
            ));

            spFeat.Children.Add(featList);
            panelFeatures.Child = spFeat;
            Grid.SetColumn(panelFeatures, 0);
            hubGrid.Children.Add(panelFeatures);

            // Right Section: Quick Access Hub
            Border panelQuick = CreateHomeSectionCard();
            StackPanel spQuick = new StackPanel();

            spQuick.Children.Add(CreateHomeCardHeader(SvgIcons.Folder, "БЫСТРЫЙ ДОСТУП"));

            string modsDir = GetModsDir();
            int modsCount = 0;
            if (Directory.Exists(modsDir))
            {
                modsCount = Directory.GetFiles(modsDir, "*.jar*").Length;
            }

            _txtHeroModsCount = new TextBlock
            {
                Text = modsCount > 0 ? (modsCount + " установленных модов") : "Моды не найдены",
                FontSize = 10.5,
                Foreground = new SolidColorBrush(AppColors.TextMuted)
            };

            StackPanel quickTiles = new StackPanel { Margin = new Thickness(0, 10, 0, 0) };

            quickTiles.Children.Add(CreateHomeActionTile(
                SvgIcons.Folder,
                "Папка игры (.minecraft / run)",
                "Конфигурации, скриншоты и миры",
                () =>
                {
                    try
                    {
                        string runDir = System.IO.Path.Combine(_projectDir, "run");
                        if (!Directory.Exists(runDir)) Directory.CreateDirectory(runDir);
                        Process.Start("explorer.exe", runDir);
                    }
                    catch { }
                }
            ));

            quickTiles.Children.Add(CreateHomeActionTile(
                SvgIcons.Cube,
                "Управление модами",
                modsCount > 0 ? (modsCount + " модов в папке mods") : "Добавить моды Fabric",
                () => SwitchPage(1)
            ));

            quickTiles.Children.Add(CreateHomeActionTile(
                SvgIcons.Search,
                "Каталог Modrinth",
                "Поиск и загрузка шейдеров и модов",
                () =>
                {
                    SwitchPage(1);
                    SwitchModsSubTab(false);
                }
            ));

            spQuick.Children.Add(quickTiles);

            panelQuick.Child = spQuick;
            Grid.SetColumn(panelQuick, 2);
            hubGrid.Children.Add(panelQuick);

            Grid.SetRow(hubGrid, 3);
            g.Children.Add(hubGrid);

            return g;
        }

        private Border CreateFeatureItem(string svgPath, string title, string description)
        {
            Border bdr = new Border
            {
                Margin = new Thickness(0, 0, 0, 12),
                Background = Brushes.Transparent
            };

            Grid g = new Grid();
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            Border iconBdr = new Border
            {
                Width = 32,
                Height = 32,
                CornerRadius = new CornerRadius(7),
                Background = new SolidColorBrush(Color.FromArgb(160, 22, 28, 42)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromArgb(40, 255, 255, 255)),
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 12, 0)
            };
            iconBdr.Child = IconHelper.CreateStrokeIcon(svgPath, 15, new SolidColorBrush(AppColors.AccentLight), 1.8);
            Grid.SetColumn(iconBdr, 0);
            g.Children.Add(iconBdr);

            StackPanel sp = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            sp.Children.Add(new TextBlock
            {
                Text = title,
                FontSize = 13,
                FontWeight = FontWeights.SemiBold,
                Foreground = Brushes.White
            });
            sp.Children.Add(new TextBlock
            {
                Text = description,
                FontSize = 11,
                Foreground = new SolidColorBrush(Color.FromRgb(148, 163, 184)),
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 2, 0, 0)
            });
            Grid.SetColumn(sp, 1);
            g.Children.Add(sp);

            bdr.Child = g;
            return bdr;
        }

        private Border CreateMinimalTag(string label)
        {
            Border b = new Border
            {
                Background = new SolidColorBrush(Color.FromArgb(120, 14, 18, 28)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromArgb(40, 255, 255, 255)),
                CornerRadius = new CornerRadius(5),
                Padding = new Thickness(8, 2.5, 8, 2.5),
                Margin = new Thickness(0, 0, 6, 0)
            };
            b.Child = new TextBlock
            {
                Text = label,
                FontSize = 10.5,
                FontWeight = FontWeights.Medium,
                Foreground = new SolidColorBrush(Color.FromRgb(203, 213, 225))
            };
            return b;
        }

        private Border CreateHomeSectionCard()
        {
            return new Border
            {
                CornerRadius = new CornerRadius(10),
                Background = new SolidColorBrush(Color.FromArgb(150, 10, 14, 22)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromArgb(40, 255, 255, 255)),
                Padding = new Thickness(20, 16, 20, 16)
            };
        }

        private Border CreateHomeActionTile(string svgPath, string title, string subtitle, Action onClick)
        {
            Border bdr = new Border
            {
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(Color.FromArgb(120, 14, 18, 28)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromArgb(35, 255, 255, 255)),
                Padding = new Thickness(12, 10, 12, 10),
                Margin = new Thickness(0, 0, 0, 8),
                Cursor = Cursors.Hand
            };

            Grid g = new Grid();
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            Border iconBdr = new Border
            {
                Width = 32,
                Height = 32,
                CornerRadius = new CornerRadius(7),
                Background = new SolidColorBrush(Color.FromArgb(180, 22, 28, 42)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromArgb(50, 255, 255, 255)),
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 10, 0)
            };
            iconBdr.Child = IconHelper.CreateStrokeIcon(svgPath, 15, new SolidColorBrush(AppColors.AccentLight), 1.8);
            Grid.SetColumn(iconBdr, 0);
            g.Children.Add(iconBdr);

            StackPanel sp = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            sp.Children.Add(new TextBlock
            {
                Text = title,
                FontSize = 12.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = Brushes.White
            });
            sp.Children.Add(new TextBlock
            {
                Text = subtitle,
                FontSize = 10.5,
                Foreground = new SolidColorBrush(Color.FromRgb(148, 163, 184)),
                Margin = new Thickness(0, 1, 0, 0)
            });
            Grid.SetColumn(sp, 1);
            g.Children.Add(sp);

            bdr.Child = g;

            bdr.MouseEnter += (s, e) =>
            {
                bdr.Background = new SolidColorBrush(Color.FromArgb(200, 26, 34, 52));
                bdr.BorderBrush = new SolidColorBrush(Color.FromArgb(90, 255, 255, 255));
            };
            bdr.MouseLeave += (s, e) =>
            {
                bdr.Background = new SolidColorBrush(Color.FromArgb(120, 14, 18, 28));
                bdr.BorderBrush = new SolidColorBrush(Color.FromArgb(35, 255, 255, 255));
            };
            bdr.MouseLeftButtonDown += (s, e) => onClick();

            return bdr;
        }

        private StackPanel CreateHomeCardHeader(string svgIcon, string title)
        {
            StackPanel sp = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
            sp.Children.Add(IconHelper.CreateStrokeIcon(svgIcon, 13, new SolidColorBrush(AppColors.TextMuted), 1.8));
            TextBlock tb = new TextBlock
            {
                Text = title,
                FontSize = 10.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(AppColors.TextMuted),
                Margin = new Thickness(6, 0, 0, 0),
                VerticalAlignment = VerticalAlignment.Center
            };
            sp.Children.Add(tb);
            return sp;
        }

        private void UpdateRamDisplays()
        {
            if (_txtDockRamBadge != null) _txtDockRamBadge.Text = (_data.RamMb / 1024) + " ГБ ОЗУ";
            if (_txtHeroRamDisplay != null) _txtHeroRamDisplay.Text = (_data.RamMb / 1024) + " ГБ ОЗУ (" + _data.RamMb + " МБ)";
            if (_txtSettingsRamMb != null) _txtSettingsRamMb.Text = _data.RamMb.ToString();
            UpdateRamPresetVisuals();
            UpdateHomeRamPillVisuals();
        }

        private void UpdateHomeRamPillVisuals()
        {
            int[] ramPresets = new int[] { 2048, 4096, 6144, 8192, 12288 };
            for (int i = 0; i < _homeRamPills.Count && i < ramPresets.Length; i++)
            {
                bool active = ramPresets[i] == _data.RamMb;
                _homeRamPills[i].Background = new SolidColorBrush(active ? AppColors.AccentPrimary : Color.FromRgb(18, 23, 35));
                _homeRamPills[i].BorderBrush = new SolidColorBrush(active ? AppColors.AccentLight : AppColors.BorderSubtle);
                _homeRamPillTexts[i].Foreground = active ? Brushes.White : new SolidColorBrush(AppColors.TextSecondary);
                _homeRamPillTexts[i].FontWeight = active ? FontWeights.Bold : FontWeights.Medium;
            }
        }

        private Border CreateDownloadBanner()
        {
            Border bdr = new Border
            {
                CornerRadius = new CornerRadius(12),
                Background = new SolidColorBrush(Color.FromArgb(235, 14, 18, 28)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromArgb(160, 30, 64, 175)),
                Padding = new Thickness(18, 14, 18, 14),
                Margin = new Thickness(0, 12, 0, 0),
                Visibility = Visibility.Collapsed
            };

            Grid g = new Grid();
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

            // Top Row: Title + Cancel Button
            Grid topRow = new Grid();
            topRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            topRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            StackPanel titleSp = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
            titleSp.Children.Add(IconHelper.CreateStrokeIcon(SvgIcons.Download, 15, new SolidColorBrush(AppColors.AccentLight), 2.0));

            _txtDownloadTitle = new TextBlock
            {
                Text = "СКАЧИВАНИЕ ФАЙЛОВ КЛИЕНТА",
                FontSize = 12.5,
                FontWeight = FontWeights.Bold,
                Foreground = Brushes.White,
                Margin = new Thickness(8, 0, 0, 0),
                VerticalAlignment = VerticalAlignment.Center
            };
            titleSp.Children.Add(_txtDownloadTitle);

            _txtDownloadStatus = new TextBlock
            {
                Text = "Подготовка...",
                FontSize = 11.5,
                Foreground = new SolidColorBrush(AppColors.AccentLight),
                Margin = new Thickness(14, 0, 0, 0),
                VerticalAlignment = VerticalAlignment.Center
            };
            titleSp.Children.Add(_txtDownloadStatus);

            Grid.SetColumn(titleSp, 0);
            topRow.Children.Add(titleSp);

            _btnCancelDownload = CreateCompactButton(SvgIcons.Close, "Отмена", (s, e) =>
            {
                CancelClientDownload();
            });
            Grid.SetColumn(_btnCancelDownload, 1);
            topRow.Children.Add(_btnCancelDownload);

            Grid.SetRow(topRow, 0);
            g.Children.Add(topRow);

            // Middle Row: Smooth Progress Bar
            Border barContainer = new Border
            {
                Height = 8,
                CornerRadius = new CornerRadius(4),
                Background = new SolidColorBrush(Color.FromRgb(20, 24, 38)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromArgb(80, 255, 255, 255)),
                Margin = new Thickness(0, 10, 0, 8),
                ClipToBounds = true
            };

            _downloadProgressBar = new ProgressBar
            {
                Height = 8,
                Minimum = 0,
                Maximum = 100,
                Value = 0,
                Background = Brushes.Transparent,
                BorderThickness = new Thickness(0),
                Foreground = new SolidColorBrush(AppColors.AccentPrimary)
            };
            barContainer.Child = _downloadProgressBar;
            Grid.SetRow(barContainer, 1);
            g.Children.Add(barContainer);

            // Bottom Row: Details stats
            _txtDownloadDetails = new TextBlock
            {
                Text = "Ожидание начала загрузки...",
                FontSize = 11,
                Foreground = new SolidColorBrush(AppColors.TextMuted)
            };
            Grid.SetRow(_txtDownloadDetails, 2);
            g.Children.Add(_txtDownloadDetails);

            bdr.Child = g;
            return bdr;
        }

        #endregion

        #region Bottom Dock

        private Border CreateBottomLaunchDock()
        {
            Border dock = new Border
            {
                Height = 72,
                CornerRadius = new CornerRadius(0, 0, 14, 14),
                Background = new SolidColorBrush(Color.FromArgb(235, 8, 11, 17)),
                BorderThickness = new Thickness(0, 1, 0, 0),
                BorderBrush = new SolidColorBrush(Color.FromArgb(35, 255, 255, 255)),
                Padding = new Thickness(24, 0, 24, 0)
            };

            Grid g = new Grid();
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });                       // Left User Profile
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) }); // Center Capsule
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });                       // Right Controls & Launch Button

            // 1. Left User Profile Widget
            StackPanel userWidget = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                VerticalAlignment = VerticalAlignment.Center,
                Cursor = Cursors.Hand
            };
            userWidget.MouseLeftButtonDown += (s, e) => SwitchPage(2);

            Border avatar = new Border
            {
                Width = 36,
                Height = 36,
                CornerRadius = new CornerRadius(18),
                Background = new SolidColorBrush(AppColors.AccentPrimary),
                VerticalAlignment = VerticalAlignment.Center
            };
            avatar.Child = IconHelper.CreateStrokeIcon(SvgIcons.User, 16, Brushes.White, 1.8);
            userWidget.Children.Add(avatar);

            StackPanel userText = new StackPanel
            {
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(10, 0, 0, 0)
            };

            _txtDockUser = new TextBlock
            {
                Text = _data.Username,
                FontSize = 13.5,
                FontWeight = FontWeights.Bold,
                Foreground = Brushes.White
            };
            userText.Children.Add(_txtDockUser);

            StackPanel statusRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 2, 0, 0) };
            _dockStatusDot = new Ellipse
            {
                Width = 7,
                Height = 7,
                Fill = new SolidColorBrush(AppColors.AccentGreen),
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 5, 0)
            };
            statusRow.Children.Add(_dockStatusDot);

            _txtDockStatus = new TextBlock
            {
                Text = "В сети",
                FontSize = 11,
                Foreground = new SolidColorBrush(AppColors.TextMuted)
            };
            statusRow.Children.Add(_txtDockStatus);
            userText.Children.Add(statusRow);

            userWidget.Children.Add(userText);
            Grid.SetColumn(userWidget, 0);
            g.Children.Add(userWidget);

            // 1.5 Center Version Capsule
            Border verPill = new Border
            {
                Height = 32,
                CornerRadius = new CornerRadius(6),
                Background = new SolidColorBrush(Color.FromArgb(90, 14, 18, 28)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromArgb(25, 255, 255, 255)),
                Padding = new Thickness(14, 0, 14, 0),
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            verPill.Child = new TextBlock
            {
                Text = "Minecraft 1.21.11 • Fabric",
                FontSize = 11.5,
                FontWeight = FontWeights.Medium,
                Foreground = new SolidColorBrush(Color.FromRgb(148, 163, 184)),
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(verPill, 1);
            g.Children.Add(verPill);

            // 2. Right Controls & Launch Button
            StackPanel rightControls = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                VerticalAlignment = VerticalAlignment.Center
            };

            // RAM Badge Shortcut
            Border ramBadge = new Border
            {
                Height = 32,
                CornerRadius = new CornerRadius(6),
                Background = new SolidColorBrush(Color.FromArgb(120, 14, 18, 28)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromArgb(35, 255, 255, 255)),
                Padding = new Thickness(12, 0, 12, 0),
                Cursor = Cursors.Hand,
                Margin = new Thickness(0, 0, 8, 0)
            };
            _txtDockRamBadge = new TextBlock
            {
                Text = (_data.RamMb / 1024) + " ГБ ОЗУ",
                FontSize = 11.5,
                FontWeight = FontWeights.Medium,
                Foreground = new SolidColorBrush(AppColors.TextSecondary),
                VerticalAlignment = VerticalAlignment.Center
            };
            ramBadge.Child = _txtDockRamBadge;
            ramBadge.MouseLeftButtonDown += (s, e) => SwitchPage(2);
            rightControls.Children.Add(ramBadge);

            // Folder Shortcut Button
            Button btnFolder = CreateDockIconButton(SvgIcons.Folder, "Папка игры (run)", (s, e) =>
            {
                try
                {
                    string runDir = System.IO.Path.Combine(_projectDir, "run");
                    if (!Directory.Exists(runDir)) Directory.CreateDirectory(runDir);
                    Process.Start("explorer.exe", runDir);
                }
                catch { }
            });
            btnFolder.Margin = new Thickness(0, 0, 8, 0);
            rightControls.Children.Add(btnFolder);

            // Terminal Shortcut Button
            Button btnTerminal = CreateDockIconButton(SvgIcons.Terminal, "Консоль логов", (s, e) => SwitchPage(3));
            btnTerminal.Margin = new Thickness(0, 0, 14, 0);
            rightControls.Children.Add(btnTerminal);

            // Centerpiece PLAY Button
            _btnDockLaunch = CreateCenterLaunchButton();
            rightControls.Children.Add(_btnDockLaunch);

            Grid.SetColumn(rightControls, 2);
            g.Children.Add(rightControls);

            dock.Child = g;
            return dock;
        }

        private Button CreateCenterLaunchButton()
        {
            Button btn = new Button
            {
                Width = 230,
                Height = 46,
                Cursor = Cursors.Hand,
                Focusable = false
            };

            StackPanel sp = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };

            _btnDockLaunchIcon = IconHelper.CreateFillIcon(SvgIcons.Play, 15, Brushes.White);
            sp.Children.Add(_btnDockLaunchIcon);

            _btnDockLaunchText = new TextBlock
            {
                Text = "ИГРАТЬ",
                FontSize = 13.5,
                FontWeight = FontWeights.Bold,
                Foreground = Brushes.White,
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(8, 0, 0, 0)
            };
            sp.Children.Add(_btnDockLaunchText);

            btn.Content = sp;

            ControlTemplate tpl = new ControlTemplate(typeof(Button));
            FrameworkElementFactory bdr = new FrameworkElementFactory(typeof(Border));
            bdr.Name = "DockLaunchBdr";
            bdr.SetValue(Border.CornerRadiusProperty, new CornerRadius(8));
            bdr.SetValue(Border.BackgroundProperty, new SolidColorBrush(AppColors.AccentPrimary));
            bdr.SetValue(Border.BorderThicknessProperty, new Thickness(1));
            bdr.SetValue(Border.BorderBrushProperty, new SolidColorBrush(AppColors.AccentLight));

            FrameworkElementFactory cp = new FrameworkElementFactory(typeof(ContentPresenter));
            cp.SetValue(ContentPresenter.HorizontalAlignmentProperty, HorizontalAlignment.Center);
            cp.SetValue(ContentPresenter.VerticalAlignmentProperty, VerticalAlignment.Center);
            bdr.AppendChild(cp);
            tpl.VisualTree = bdr;

            Trigger hov = new Trigger { Property = Button.IsMouseOverProperty, Value = true };
            hov.Setters.Add(new Setter(Border.BackgroundProperty, new SolidColorBrush(AppColors.AccentHover), "DockLaunchBdr"));
            tpl.Triggers.Add(hov);

            btn.Template = tpl;
            btn.Effect = null;

            btn.Click += (s, e) => OnLaunchButtonClick();
            return btn;
        }

        private Button CreateDockIconButton(string svgPath, string tooltip, RoutedEventHandler onClick)
        {
            Button btn = new Button
            {
                Width = 34,
                Height = 32,
                Cursor = Cursors.Hand,
                Focusable = false,
                ToolTip = tooltip
            };

            SPath p = IconHelper.CreateStrokeIcon(svgPath, 14, new SolidColorBrush(AppColors.TextSecondary), 1.8);
            btn.Content = p;

            ControlTemplate tpl = new ControlTemplate(typeof(Button));
            FrameworkElementFactory bdr = new FrameworkElementFactory(typeof(Border));
            bdr.Name = "Bdr";
            bdr.SetValue(Border.CornerRadiusProperty, new CornerRadius(8));
            bdr.SetValue(Border.BackgroundProperty, new SolidColorBrush(Color.FromArgb(160, 22, 28, 42)));
            bdr.SetValue(Border.BorderThicknessProperty, new Thickness(1));
            bdr.SetValue(Border.BorderBrushProperty, new SolidColorBrush(AppColors.BorderSubtle));

            FrameworkElementFactory cp = new FrameworkElementFactory(typeof(ContentPresenter));
            cp.SetValue(ContentPresenter.HorizontalAlignmentProperty, HorizontalAlignment.Center);
            cp.SetValue(ContentPresenter.VerticalAlignmentProperty, VerticalAlignment.Center);
            bdr.AppendChild(cp);

            tpl.VisualTree = bdr;

            Trigger hov = new Trigger { Property = Button.IsMouseOverProperty, Value = true };
            hov.Setters.Add(new Setter(Border.BackgroundProperty, new SolidColorBrush(Color.FromRgb(32, 40, 60)), "Bdr"));
            hov.Setters.Add(new Setter(Border.BorderBrushProperty, new SolidColorBrush(AppColors.BorderHover), "Bdr"));
            tpl.Triggers.Add(hov);

            btn.Template = tpl;
            btn.Click += onClick;

            btn.MouseEnter += (s, e) => p.Stroke = Brushes.White;
            btn.MouseLeave += (s, e) => p.Stroke = new SolidColorBrush(AppColors.TextSecondary);

            return btn;
        }

        #endregion

        #region Page 2: Настройки (Compact macOS / Discord Style Cards)

        private Grid CreatePageSettings()
        {
            Grid g = new Grid();
            ScrollViewer sv = new ScrollViewer
            {
                VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
                HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled
            };

            StackPanel sp = new StackPanel();

            // 1. Card: Игровой аккаунт (Компактный инлайн ряд)
            Border cardUser = CreateSettingsGroupCard();
            Grid userGrid = new Grid();
            userGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            userGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            StackPanel userLabels = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            userLabels.Children.Add(new TextBlock
            {
                Text = "Никнейм игрока",
                FontSize = 13,
                FontWeight = FontWeights.SemiBold,
                Foreground = Brushes.White
            });
            userLabels.Children.Add(new TextBlock
            {
                Text = "Имя вашего персонажа в одиночной игре и мультиплеере",
                FontSize = 11,
                Foreground = new SolidColorBrush(AppColors.TextMuted),
                Margin = new Thickness(0, 2, 0, 0)
            });
            Grid.SetColumn(userLabels, 0);
            userGrid.Children.Add(userLabels);

            StackPanel userInputs = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                VerticalAlignment = VerticalAlignment.Center
            };

            Border bdrUserInput = new Border
            {
                Width = 160,
                Height = 32,
                CornerRadius = new CornerRadius(7),
                Background = new SolidColorBrush(AppColors.InputBg),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(AppColors.InputBorder),
                Padding = new Thickness(10, 0, 10, 0),
                Margin = new Thickness(0, 0, 8, 0)
            };
            _txtSettingsUser = new TextBox
            {
                Text = _data.Username,
                FontSize = 12.5,
                Foreground = Brushes.White,
                Background = Brushes.Transparent,
                BorderThickness = new Thickness(0),
                VerticalAlignment = VerticalAlignment.Center
            };
            bdrUserInput.Child = _txtSettingsUser;
            userInputs.Children.Add(bdrUserInput);

            Button btnSaveUser = CreateCompactButton(SvgIcons.Check, "Сохранить", (s, e) =>
            {
                string u = _txtSettingsUser.Text.Trim();
                if (!string.IsNullOrEmpty(u))
                {
                    _data.Username = u;
                    _data.Save();
                    if (_txtDockUser != null) _txtDockUser.Text = u;
                    if (_txtHeroUsernameDisplay != null) _txtHeroUsernameDisplay.Text = u;
                    System.Windows.MessageBox.Show("Никнейм сохранен: " + u, "Настройки", MessageBoxButton.OK, MessageBoxImage.Information);
                }
            });
            userInputs.Children.Add(btnSaveUser);

            Grid.SetColumn(userInputs, 1);
            userGrid.Children.Add(userInputs);
            cardUser.Child = userGrid;
            sp.Children.Add(cardUser);

            // 2. Card: Оперативная память (RAM) - Сегментированные пиллы
            Border cardRam = CreateSettingsGroupCard();
            cardRam.Margin = new Thickness(0, 10, 0, 0);

            StackPanel ramLayout = new StackPanel();

            Grid ramHeaderRow = new Grid();
            ramHeaderRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            ramHeaderRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            StackPanel ramLabels = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            ramLabels.Children.Add(new TextBlock
            {
                Text = "Выделение оперативной памяти (RAM)",
                FontSize = 13,
                FontWeight = FontWeights.SemiBold,
                Foreground = Brushes.White
            });
            ramLabels.Children.Add(new TextBlock
            {
                Text = "Объем памяти для Java Virtual Machine (-Xmx). Рекомендуется 4–6 ГБ",
                FontSize = 11,
                Foreground = new SolidColorBrush(AppColors.TextMuted),
                Margin = new Thickness(0, 2, 0, 0)
            });
            Grid.SetColumn(ramLabels, 0);
            ramHeaderRow.Children.Add(ramLabels);

            // Precise MB Input
            StackPanel exactRow = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
            Border bdrMb = new Border
            {
                Width = 72,
                Height = 32,
                CornerRadius = new CornerRadius(7),
                Background = new SolidColorBrush(AppColors.InputBg),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(AppColors.InputBorder),
                Margin = new Thickness(0, 0, 8, 0)
            };
            _txtSettingsRamMb = new TextBox
            {
                Text = _data.RamMb.ToString(),
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                Foreground = Brushes.White,
                Background = Brushes.Transparent,
                BorderThickness = new Thickness(0),
                HorizontalContentAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            bdrMb.Child = _txtSettingsRamMb;
            exactRow.Children.Add(bdrMb);

            Button btnApplyMb = CreateCompactButton(SvgIcons.Check, "МБ", (s, e) =>
            {
                int v;
                if (int.TryParse(_txtSettingsRamMb.Text.Trim(), out v) && v >= 1024)
                {
                    _data.RamMb = v;
                    _data.Save();
                    if (_txtDockRamBadge != null) _txtDockRamBadge.Text = (v / 1024) + " ГБ ОЗУ";
                    UpdateRamPresetVisuals();
                    System.Windows.MessageBox.Show("Память обновлена: " + v + " МБ", "Настройки", MessageBoxButton.OK, MessageBoxImage.Information);
                }
            });
            exactRow.Children.Add(btnApplyMb);

            Grid.SetColumn(exactRow, 1);
            ramHeaderRow.Children.Add(exactRow);
            ramLayout.Children.Add(ramHeaderRow);

            // RAM Segmented Pills
            StackPanel ramPillRow = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                Margin = new Thickness(0, 12, 0, 0)
            };
            int[] ramValues = new int[] { 2048, 4096, 6144, 8192, 12288, 16384 };

            foreach (int val in ramValues)
            {
                int mb = val;
                Border pillBox = new Border
                {
                    Height = 30,
                    CornerRadius = new CornerRadius(6),
                    Background = new SolidColorBrush(mb == _data.RamMb ? AppColors.AccentPrimary : Color.FromRgb(20, 25, 38)),
                    BorderThickness = new Thickness(1),
                    BorderBrush = new SolidColorBrush(mb == _data.RamMb ? AppColors.AccentLight : AppColors.BorderSubtle),
                    Padding = new Thickness(14, 0, 14, 0),
                    Margin = new Thickness(0, 0, 6, 0),
                    Cursor = Cursors.Hand
                };

                TextBlock tb = new TextBlock
                {
                    Text = (mb / 1024) + " ГБ",
                    FontSize = 11.5,
                    FontWeight = mb == _data.RamMb ? FontWeights.Bold : FontWeights.Medium,
                    Foreground = mb == _data.RamMb ? Brushes.White : new SolidColorBrush(AppColors.TextSecondary),
                    VerticalAlignment = VerticalAlignment.Center
                };
                pillBox.Child = tb;

                _ramPillBoxes.Add(pillBox);
                _ramPillTexts.Add(tb);

                pillBox.MouseLeftButtonDown += (s, e) =>
                {
                    _data.RamMb = mb;
                    _data.Save();
                    _txtSettingsRamMb.Text = mb.ToString();
                    if (_txtDockRamBadge != null) _txtDockRamBadge.Text = (mb / 1024) + " ГБ ОЗУ";
                    UpdateRamPresetVisuals();
                };

                ramPillRow.Children.Add(pillBox);
            }
            ramLayout.Children.Add(ramPillRow);

            cardRam.Child = ramLayout;
            sp.Children.Add(cardRam);

            // 3. Card: Параметры JVM (Флаги оптимизации)
            Border cardJvm = CreateSettingsGroupCard();
            cardJvm.Margin = new Thickness(0, 10, 0, 0);

            StackPanel jvmLayout = new StackPanel();

            Grid jvmHeader = new Grid();
            jvmHeader.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            jvmHeader.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            StackPanel jvmLabels = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            jvmLabels.Children.Add(new TextBlock
            {
                Text = "Параметры Java Virtual Machine (JVM)",
                FontSize = 13,
                FontWeight = FontWeights.SemiBold,
                Foreground = Brushes.White
            });
            jvmLabels.Children.Add(new TextBlock
            {
                Text = "Флаги сборщика мусора и производительности",
                FontSize = 11,
                Foreground = new SolidColorBrush(AppColors.TextMuted),
                Margin = new Thickness(0, 2, 0, 0)
            });
            Grid.SetColumn(jvmLabels, 0);
            jvmHeader.Children.Add(jvmLabels);

            StackPanel jvmPresets = new StackPanel { Orientation = Orientation.Horizontal };

            Button btnG1 = CreateCompactButton(null, "G1GC (Дефолт)", (s, e) =>
            {
                _data.JvmArgs = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200";
                _txtSettingsJvm.Text = _data.JvmArgs;
                _data.Save();
            });
            jvmPresets.Children.Add(btnG1);

            Button btnZgc = CreateCompactButton(null, "ZGC (Low-Latency)", (s, e) =>
            {
                _data.JvmArgs = "-XX:+UseZGC -XX:+ZGenerational";
                _txtSettingsJvm.Text = _data.JvmArgs;
                _data.Save();
            });
            btnZgc.Margin = new Thickness(6, 0, 0, 0);
            jvmPresets.Children.Add(btnZgc);

            Grid.SetColumn(jvmPresets, 1);
            jvmHeader.Children.Add(jvmPresets);
            jvmLayout.Children.Add(jvmHeader);

            Border bdrJvm = new Border
            {
                Height = 34,
                CornerRadius = new CornerRadius(7),
                Background = new SolidColorBrush(AppColors.InputBg),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(AppColors.InputBorder),
                Padding = new Thickness(10, 0, 10, 0),
                Margin = new Thickness(0, 10, 0, 0)
            };
            _txtSettingsJvm = new TextBox
            {
                Text = _data.JvmArgs,
                FontSize = 11.5,
                FontFamily = new FontFamily("Consolas, Courier New"),
                Foreground = Brushes.White,
                Background = Brushes.Transparent,
                BorderThickness = new Thickness(0),
                VerticalAlignment = VerticalAlignment.Center
            };
            _txtSettingsJvm.LostFocus += (s, e) =>
            {
                _data.JvmArgs = _txtSettingsJvm.Text.Trim();
                _data.Save();
            };
            bdrJvm.Child = _txtSettingsJvm;
            jvmLayout.Children.Add(bdrJvm);

            cardJvm.Child = jvmLayout;
            sp.Children.Add(cardJvm);

            // 4. Card: Каталог клиента
            Border cardDir = CreateSettingsGroupCard();
            cardDir.Margin = new Thickness(0, 10, 0, 0);

            Grid dirGrid = new Grid();
            dirGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            dirGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            StackPanel dirLabels = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            dirLabels.Children.Add(new TextBlock
            {
                Text = "Рабочая папка проекта",
                FontSize = 13,
                FontWeight = FontWeights.SemiBold,
                Foreground = Brushes.White
            });

            _txtSettingsPath = new TextBlock
            {
                Text = _projectDir,
                FontSize = 11,
                Foreground = new SolidColorBrush(AppColors.TextMuted),
                TextTrimming = TextTrimming.CharacterEllipsis,
                MaxWidth = 450,
                Margin = new Thickness(0, 2, 0, 0)
            };
            dirLabels.Children.Add(_txtSettingsPath);
            Grid.SetColumn(dirLabels, 0);
            dirGrid.Children.Add(dirLabels);

            StackPanel dirBtns = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                VerticalAlignment = VerticalAlignment.Center
            };

            Button btnBrowse = CreateCompactButton(SvgIcons.Folder, "Обзор...", (s, e) =>
            {
                System.Windows.Forms.FolderBrowserDialog fbd = new System.Windows.Forms.FolderBrowserDialog();
                fbd.Description = "Выберите корневую папку с проектом RainyDLC";
                if (fbd.ShowDialog() == System.Windows.Forms.DialogResult.OK)
                {
                    if (File.Exists(System.IO.Path.Combine(fbd.SelectedPath, "gradlew.bat")))
                    {
                        _projectDir = fbd.SelectedPath;
                        _data.CustomProjectPath = _projectDir;
                        _txtSettingsPath.Text = _projectDir;
                        _data.Save();
                        ReloadLocalMods();
                    }
                    else
                    {
                        System.Windows.MessageBox.Show("В выбранной папке не найден gradlew.bat!", "Ошибка", MessageBoxButton.OK, MessageBoxImage.Warning);
                    }
                }
            });
            dirBtns.Children.Add(btnBrowse);

            Button btnOpen = CreateCompactButton(SvgIcons.Folder, "Открыть", (s, e) =>
            {
                try { Process.Start("explorer.exe", _projectDir); } catch { }
            });
            btnOpen.Margin = new Thickness(6, 0, 0, 0);
            dirBtns.Children.Add(btnOpen);

            Grid.SetColumn(dirBtns, 1);
            dirGrid.Children.Add(dirBtns);
            cardDir.Child = dirGrid;
            sp.Children.Add(cardDir);

            // 5. Card: Файлы клиента и автообновление
            Border cardClient = CreateSettingsGroupCard();
            cardClient.Margin = new Thickness(0, 10, 0, 0);

            StackPanel clientSp = new StackPanel();

            TextBlock titleClient = new TextBlock
            {
                Text = "Файлы клиента и автозагрузка",
                FontSize = 13,
                FontWeight = FontWeights.SemiBold,
                Foreground = Brushes.White,
                Margin = new Thickness(0, 0, 0, 8)
            };
            clientSp.Children.Add(titleClient);

            _txtSettingsClientStatus = new TextBlock
            {
                Text = "Статус файлов: Проверка...",
                FontSize = 11.5,
                Foreground = new SolidColorBrush(AppColors.TextSecondary),
                Margin = new Thickness(0, 0, 0, 10)
            };
            clientSp.Children.Add(_txtSettingsClientStatus);

            TextBlock lblUrl = new TextBlock
            {
                Text = "URL загрузки файлов клиента (rainydlc.jar):",
                FontSize = 11,
                Foreground = new SolidColorBrush(AppColors.TextMuted),
                Margin = new Thickness(0, 0, 0, 4)
            };
            clientSp.Children.Add(lblUrl);

            Border bdrUrl = new Border
            {
                Height = 34,
                CornerRadius = new CornerRadius(7),
                Background = new SolidColorBrush(AppColors.InputBg),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(AppColors.InputBorder),
                Padding = new Thickness(10, 0, 10, 0),
                Margin = new Thickness(0, 0, 0, 10)
            };
            _txtSettingsDownloadUrl = new TextBox
            {
                Text = _data.ClientDownloadUrl,
                FontSize = 11.5,
                FontFamily = new FontFamily("Consolas, Courier New"),
                Foreground = Brushes.White,
                Background = Brushes.Transparent,
                BorderThickness = new Thickness(0),
                VerticalAlignment = VerticalAlignment.Center
            };
            _txtSettingsDownloadUrl.LostFocus += (s, e) =>
            {
                _data.ClientDownloadUrl = _txtSettingsDownloadUrl.Text.Trim();
                _data.Save();
            };
            bdrUrl.Child = _txtSettingsDownloadUrl;
            clientSp.Children.Add(bdrUrl);

            StackPanel clientBtns = new StackPanel
            {
                Orientation = Orientation.Horizontal
            };

            _btnSettingsDownloadClient = CreateCompactButton(SvgIcons.Download, "Скачать клиент", (s, e) =>
            {
                StartClientDownload(false);
            });
            clientBtns.Children.Add(_btnSettingsDownloadClient);

            Button btnDownloadFabricApi = CreateCompactButton(SvgIcons.Sparkles, "Скачать Fabric API", (s, e) =>
            {
                ThreadPool.QueueUserWorkItem(delegate
                {
                    EnsureFabricApiInstalled(GetModsDir());
                    Dispatcher.BeginInvoke(new Action(() =>
                    {
                        ReloadLocalMods();
                        UpdateClientStatusUI();
                        System.Windows.MessageBox.Show("Fabric API проверен и установлен!", "RainyDLC", MessageBoxButton.OK, MessageBoxImage.Information);
                    }));
                });
            });
            btnDownloadFabricApi.Margin = new Thickness(8, 0, 0, 0);
            clientBtns.Children.Add(btnDownloadFabricApi);

            Button btnOpenModsFolder = CreateCompactButton(SvgIcons.Folder, "Папка mods", (s, e) =>
            {
                try { Process.Start("explorer.exe", GetModsDir()); } catch { }
            });
            btnOpenModsFolder.Margin = new Thickness(8, 0, 0, 0);
            clientBtns.Children.Add(btnOpenModsFolder);

            clientSp.Children.Add(clientBtns);
            cardClient.Child = clientSp;
            sp.Children.Add(cardClient);

            sv.Content = sp;
            g.Children.Add(sv);
            return g;
        }

        private Border CreateSettingsGroupCard()
        {
            return new Border
            {
                CornerRadius = new CornerRadius(12),
                Background = new SolidColorBrush(AppColors.CardBg),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(AppColors.BorderSubtle),
                Padding = new Thickness(16, 14, 16, 14)
            };
        }

        private Button CreateCompactButton(string svgPath, string label, RoutedEventHandler onClick)
        {
            Button btn = new Button
            {
                Height = 32,
                Cursor = Cursors.Hand,
                Focusable = false,
                Padding = new Thickness(12, 0, 12, 0)
            };

            StackPanel sp = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                VerticalAlignment = VerticalAlignment.Center
            };
            if (!string.IsNullOrEmpty(svgPath))
            {
                sp.Children.Add(IconHelper.CreateStrokeIcon(svgPath, 12, new SolidColorBrush(AppColors.TextSecondary), 1.8));
            }
            TextBlock tb = new TextBlock
            {
                Text = label,
                FontSize = 11.5,
                FontWeight = FontWeights.Medium,
                Foreground = new SolidColorBrush(AppColors.TextSecondary),
                Margin = string.IsNullOrEmpty(svgPath) ? new Thickness(0) : new Thickness(6, 0, 0, 0),
                VerticalAlignment = VerticalAlignment.Center
            };
            sp.Children.Add(tb);
            btn.Content = sp;

            ControlTemplate tpl = new ControlTemplate(typeof(Button));
            FrameworkElementFactory bdr = new FrameworkElementFactory(typeof(Border));
            bdr.Name = "Bdr";
            bdr.SetValue(Border.CornerRadiusProperty, new CornerRadius(6));
            bdr.SetValue(Border.BackgroundProperty, new SolidColorBrush(Color.FromRgb(20, 25, 38)));
            bdr.SetValue(Border.BorderThicknessProperty, new Thickness(1));
            bdr.SetValue(Border.BorderBrushProperty, new SolidColorBrush(AppColors.BorderSubtle));

            FrameworkElementFactory cp = new FrameworkElementFactory(typeof(ContentPresenter));
            cp.SetValue(ContentPresenter.HorizontalAlignmentProperty, HorizontalAlignment.Center);
            cp.SetValue(ContentPresenter.VerticalAlignmentProperty, VerticalAlignment.Center);
            bdr.AppendChild(cp);
            tpl.VisualTree = bdr;

            Trigger hov = new Trigger { Property = Button.IsMouseOverProperty, Value = true };
            hov.Setters.Add(new Setter(Border.BackgroundProperty, new SolidColorBrush(Color.FromRgb(30, 38, 58)), "Bdr"));
            hov.Setters.Add(new Setter(Border.BorderBrushProperty, new SolidColorBrush(AppColors.BorderHover), "Bdr"));
            tpl.Triggers.Add(hov);

            btn.Template = tpl;
            btn.Click += onClick;
            return btn;
        }

        private void UpdateRamPresetVisuals()
        {
            int[] ramValues = new int[] { 2048, 4096, 6144, 8192, 12288, 16384 };
            for (int i = 0; i < _ramPillBoxes.Count && i < ramValues.Length; i++)
            {
                bool active = ramValues[i] == _data.RamMb;
                _ramPillBoxes[i].Background = new SolidColorBrush(active ? AppColors.AccentPrimary : Color.FromRgb(20, 25, 38));
                _ramPillBoxes[i].BorderBrush = new SolidColorBrush(active ? AppColors.AccentLight : AppColors.BorderSubtle);
                _ramPillTexts[i].Foreground = active ? Brushes.White : new SolidColorBrush(AppColors.TextSecondary);
                _ramPillTexts[i].FontWeight = active ? FontWeights.Bold : FontWeights.Medium;
            }
        }

        #endregion

        #region Page 1: Моды (Local & Modrinth Online)

        private Grid CreatePageMods()
        {
            Grid g = new Grid();
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto }); // Header & Subtabs
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(12, GridUnitType.Pixel) }); // Gap
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });   // Views

            // Header & Subtabs
            Grid topRow = new Grid();
            topRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            topRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            topRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            // Subtabs [ Установленные ] & [ Modrinth ]
            Border subtabsContainer = new Border
            {
                Background = new SolidColorBrush(Color.FromArgb(160, 16, 20, 30)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(AppColors.BorderSubtle),
                CornerRadius = new CornerRadius(8),
                Padding = new Thickness(3)
            };

            StackPanel tabs = new StackPanel { Orientation = Orientation.Horizontal };

            _tabLocalBtn = new Border
            {
                CornerRadius = new CornerRadius(6),
                Background = new SolidColorBrush(AppColors.AccentPrimary),
                Padding = new Thickness(14, 5, 14, 5),
                Cursor = Cursors.Hand
            };
            _tabLocalText = new TextBlock
            {
                Text = "Установленные",
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                Foreground = Brushes.White
            };
            _tabLocalBtn.Child = _tabLocalText;
            _tabLocalBtn.MouseLeftButtonDown += (s, e) => SwitchModsSubTab(true);
            tabs.Children.Add(_tabLocalBtn);

            _tabModrinthBtn = new Border
            {
                CornerRadius = new CornerRadius(6),
                Background = Brushes.Transparent,
                Padding = new Thickness(14, 5, 14, 5),
                Cursor = Cursors.Hand
            };
            _tabModrinthText = new TextBlock
            {
                Text = "Каталог Modrinth",
                FontSize = 12,
                Foreground = new SolidColorBrush(AppColors.TextSecondary)
            };
            _tabModrinthBtn.Child = _tabModrinthText;
            _tabModrinthBtn.MouseLeftButtonDown += (s, e) => SwitchModsSubTab(false);
            tabs.Children.Add(_tabModrinthBtn);

            subtabsContainer.Child = tabs;
            Grid.SetColumn(subtabsContainer, 0);
            topRow.Children.Add(subtabsContainer);

            // Right Action Buttons
            StackPanel actBtns = new StackPanel { Orientation = Orientation.Horizontal };

            Button btnModFolder = CreateCompactButton(SvgIcons.Folder, "Папка модов", (s, e) =>
            {
                string md = GetModsDir();
                try { Process.Start("explorer.exe", md); } catch { }
            });
            actBtns.Children.Add(btnModFolder);

            Button btnRefresh = CreateCompactButton(SvgIcons.Refresh, "Обновить", (s, e) =>
            {
                ReloadLocalMods();
                if (_modsModrinthView.Visibility == Visibility.Visible)
                {
                    LoadModrinthMods(_txtModrinthSearch != null ? _txtModrinthSearch.Text.Trim() : "");
                }
            });
            btnRefresh.Margin = new Thickness(8, 0, 0, 0);
            actBtns.Children.Add(btnRefresh);

            Grid.SetColumn(actBtns, 2);
            topRow.Children.Add(actBtns);

            Grid.SetRow(topRow, 0);
            g.Children.Add(topRow);

            // Subviews
            _modsLocalView = CreateLocalModsView();
            _modsModrinthView = CreateModrinthView();

            Grid.SetRow(_modsLocalView, 2);
            Grid.SetRow(_modsModrinthView, 2);

            g.Children.Add(_modsLocalView);
            g.Children.Add(_modsModrinthView);

            _modsLocalView.Visibility = Visibility.Visible;
            _modsModrinthView.Visibility = Visibility.Collapsed;

            return g;
        }

        private void SwitchModsSubTab(bool showLocal)
        {
            if (showLocal)
            {
                _tabLocalBtn.Background = new SolidColorBrush(AppColors.AccentPrimary);
                _tabLocalText.Foreground = Brushes.White;
                _tabLocalText.FontWeight = FontWeights.SemiBold;

                _tabModrinthBtn.Background = Brushes.Transparent;
                _tabModrinthText.Foreground = new SolidColorBrush(AppColors.TextSecondary);
                _tabModrinthText.FontWeight = FontWeights.Normal;

                _modsLocalView.Visibility = Visibility.Visible;
                _modsModrinthView.Visibility = Visibility.Collapsed;

                ReloadLocalMods();
            }
            else
            {
                _tabModrinthBtn.Background = new SolidColorBrush(AppColors.AccentPrimary);
                _tabModrinthText.Foreground = Brushes.White;
                _tabModrinthText.FontWeight = FontWeights.SemiBold;

                _tabLocalBtn.Background = Brushes.Transparent;
                _tabLocalText.Foreground = new SolidColorBrush(AppColors.TextSecondary);
                _tabLocalText.FontWeight = FontWeights.Normal;

                _modsLocalView.Visibility = Visibility.Collapsed;
                _modsModrinthView.Visibility = Visibility.Visible;

                if (!_modrinthLoadedOnce)
                {
                    _modrinthLoadedOnce = true;
                    LoadModrinthMods("");
                }
            }
        }

        private Grid CreateLocalModsView()
        {
            Grid g = new Grid();
            ScrollViewer sv = new ScrollViewer { VerticalScrollBarVisibility = ScrollBarVisibility.Auto };

            _localModsStack = new StackPanel();
            sv.Content = _localModsStack;

            g.Children.Add(sv);
            ReloadLocalMods();

            return g;
        }

        private void ReloadLocalMods()
        {
            if (_localModsStack == null) return;
            _localModsStack.Children.Clear();

            string md = GetModsDir();
            List<string> jarFiles = new List<string>();

            if (Directory.Exists(md))
            {
                jarFiles.AddRange(Directory.GetFiles(md, "*.jar"));
                jarFiles.AddRange(Directory.GetFiles(md, "*.jar.disabled"));
            }

            if (_tabLocalText != null)
            {
                _tabLocalText.Text = "Установленные (" + jarFiles.Count + ")";
            }

            if (jarFiles.Count == 0)
            {
                // Compact Empty State
                Border emptyCard = new Border
                {
                    CornerRadius = new CornerRadius(12),
                    Background = new SolidColorBrush(AppColors.CardBg),
                    BorderThickness = new Thickness(1),
                    BorderBrush = new SolidColorBrush(AppColors.BorderSubtle),
                    Padding = new Thickness(20, 24, 20, 24),
                    Margin = new Thickness(0, 0, 0, 14)
                };

                StackPanel spEmpty = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center };
                spEmpty.Children.Add(IconHelper.CreateStrokeIcon(SvgIcons.Cube, 32, new SolidColorBrush(AppColors.AccentPrimary), 1.8));

                spEmpty.Children.Add(new TextBlock
                {
                    Text = "В папке run/mods пока нет модов",
                    FontSize = 14,
                    FontWeight = FontWeights.Bold,
                    Foreground = Brushes.White,
                    HorizontalAlignment = HorizontalAlignment.Center,
                    Margin = new Thickness(0, 8, 0, 4)
                });

                spEmpty.Children.Add(new TextBlock
                {
                    Text = "Скачайте моды из каталога Modrinth ниже или добавьте файлы в папку",
                    FontSize = 11.5,
                    Foreground = new SolidColorBrush(AppColors.TextMuted),
                    HorizontalAlignment = HorizontalAlignment.Center,
                    Margin = new Thickness(0, 0, 0, 14)
                });

                Button btnBrowseOnline = CreateCompactButton(SvgIcons.Download, "Открыть каталог Modrinth", (s, e) => SwitchModsSubTab(false));
                btnBrowseOnline.HorizontalAlignment = HorizontalAlignment.Center;
                spEmpty.Children.Add(btnBrowseOnline);

                emptyCard.Child = spEmpty;
                _localModsStack.Children.Add(emptyCard);
            }
            else
            {
                // Compact Grid of Installed Mods
                Grid modGrid = new Grid { Margin = new Thickness(0, 0, 0, 14) };
                modGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                modGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(10, GridUnitType.Pixel) });
                modGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

                StackPanel colLeft = new StackPanel();
                StackPanel colRight = new StackPanel();

                for (int i = 0; i < jarFiles.Count; i++)
                {
                    string filePath = jarFiles[i];
                    string fileName = System.IO.Path.GetFileName(filePath);
                    bool isEnabled = !fileName.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase);

                    FileInfo fi = new FileInfo(filePath);
                    string sizeStr = (fi.Length / 1024.0 / 1024.0).ToString("0.#") + " MB";

                    Border card = CreateInstalledModCard(fileName, sizeStr, isEnabled, (active) =>
                    {
                        try
                        {
                            if (active && fileName.EndsWith(".disabled"))
                            {
                                string newPath = filePath.Substring(0, filePath.Length - 9);
                                if (!File.Exists(newPath)) File.Move(filePath, newPath);
                            }
                            else if (!active && !fileName.EndsWith(".disabled"))
                            {
                                string newPath = filePath + ".disabled";
                                if (!File.Exists(newPath)) File.Move(filePath, newPath);
                            }
                        }
                        catch { }
                    });

                    if (i % 2 == 0) colLeft.Children.Add(card);
                    else colRight.Children.Add(card);
                }

                Grid.SetColumn(colLeft, 0);
                modGrid.Children.Add(colLeft);

                Grid.SetColumn(colRight, 2);
                modGrid.Children.Add(colRight);

                _localModsStack.Children.Add(modGrid);
            }

            // Recommended Essential Mods Section
            TextBlock txtRec = new TextBlock
            {
                Text = "Рекомендуемые моды для Fabric 1.21",
                FontSize = 13,
                FontWeight = FontWeights.Bold,
                Foreground = Brushes.White,
                Margin = new Thickness(0, 4, 0, 8)
            };
            _localModsStack.Children.Add(txtRec);

            Grid recGrid = new Grid();
            recGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            recGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(10, GridUnitType.Pixel) });
            recGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            StackPanel rLeft = new StackPanel();
            StackPanel rRight = new StackPanel();

            rLeft.Children.Add(CreateRecommendedModCard("sodium", "Sodium", "Графическая оптимизация движка рендеринга"));
            rLeft.Children.Add(CreateRecommendedModCard("iris", "Iris Shaders", "Поддержка шейдеров с высоким FPS"));
            rLeft.Children.Add(CreateRecommendedModCard("ferrite-core", "FerriteCore", "Снижение потребления оперативной памяти"));

            rRight.Children.Add(CreateRecommendedModCard("lithium", "Lithium", "Оптимизация тиков физики и мобов"));
            rRight.Children.Add(CreateRecommendedModCard("appleskin", "AppleSkin", "Отображение сытости и насыщения в HUD"));
            rRight.Children.Add(CreateRecommendedModCard("fabric-api", "Fabric API", "Библиотека хуков Fabric"));

            Grid.SetColumn(rLeft, 0);
            recGrid.Children.Add(rLeft);

            Grid.SetColumn(rRight, 2);
            recGrid.Children.Add(rRight);

            _localModsStack.Children.Add(recGrid);
        }

        private Border CreateInstalledModCard(string fileName, string size, bool isEnabled, Action<bool> onToggle)
        {
            Border b = new Border
            {
                Height = 58,
                CornerRadius = new CornerRadius(10),
                Background = new SolidColorBrush(AppColors.CardBg),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(AppColors.BorderSubtle),
                Padding = new Thickness(12, 0, 12, 0),
                Margin = new Thickness(0, 0, 0, 8)
            };

            Grid g = new Grid();
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            Border iconBorder = new Border
            {
                Width = 30,
                Height = 30,
                CornerRadius = new CornerRadius(7),
                Background = new SolidColorBrush(Color.FromRgb(22, 28, 42)),
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 10, 0)
            };
            iconBorder.Child = IconHelper.CreateStrokeIcon(SvgIcons.Cube, 14, new SolidColorBrush(AppColors.AccentPrimary), 1.8);
            Grid.SetColumn(iconBorder, 0);
            g.Children.Add(iconBorder);

            StackPanel sp = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            string cleanTitle = fileName.Replace(".jar.disabled", "").Replace(".jar", "");
            sp.Children.Add(new TextBlock
            {
                Text = cleanTitle,
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                Foreground = Brushes.White,
                TextTrimming = TextTrimming.CharacterEllipsis
            });
            sp.Children.Add(new TextBlock
            {
                Text = size + " • " + (isEnabled ? "Активен" : "Отключен"),
                FontSize = 10,
                Foreground = new SolidColorBrush(AppColors.TextMuted),
                Margin = new Thickness(0, 2, 0, 0)
            });
            Grid.SetColumn(sp, 1);
            g.Children.Add(sp);

            ToggleSwitch sw = new ToggleSwitch(isEnabled) { VerticalAlignment = VerticalAlignment.Center };
            sw.CheckedChanged += onToggle;
            Grid.SetColumn(sw, 2);
            g.Children.Add(sw);

            b.Child = g;
            return b;
        }

        private Border CreateRecommendedModCard(string slug, string title, string desc)
        {
            Border b = new Border
            {
                Height = 56,
                CornerRadius = new CornerRadius(10),
                Background = new SolidColorBrush(AppColors.CardBg),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(AppColors.BorderSubtle),
                Padding = new Thickness(12, 0, 12, 0),
                Margin = new Thickness(0, 0, 0, 8)
            };

            Grid g = new Grid();
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            StackPanel sp = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            sp.Children.Add(new TextBlock
            {
                Text = title,
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                Foreground = Brushes.White
            });
            sp.Children.Add(new TextBlock
            {
                Text = desc,
                FontSize = 10,
                Foreground = new SolidColorBrush(AppColors.TextMuted),
                TextTrimming = TextTrimming.CharacterEllipsis,
                Margin = new Thickness(0, 1, 0, 0)
            });
            Grid.SetColumn(sp, 0);
            g.Children.Add(sp);

            string dummy;
            bool installed = IsModInstalled(slug, title, out dummy);

            Button btn = new Button
            {
                Height = 26,
                Cursor = Cursors.Hand,
                Focusable = false,
                Padding = new Thickness(10, 0, 10, 0),
                VerticalAlignment = VerticalAlignment.Center
            };

            TextBlock btnTxt = new TextBlock
            {
                Text = installed ? "✓ Есть" : "Скачать",
                FontSize = 10.5,
                FontWeight = FontWeights.Medium,
                Foreground = installed ? new SolidColorBrush(AppColors.AccentGreen) : Brushes.White
            };
            btn.Content = btnTxt;

            ControlTemplate tpl = new ControlTemplate(typeof(Button));
            FrameworkElementFactory bdr = new FrameworkElementFactory(typeof(Border));
            bdr.Name = "RecBdr";
            bdr.SetValue(Border.CornerRadiusProperty, new CornerRadius(5));
            bdr.SetValue(Border.BackgroundProperty, installed ? new SolidColorBrush(Color.FromArgb(32, 16, 185, 129)) : new SolidColorBrush(AppColors.AccentPrimary));
            bdr.SetValue(Border.BorderThicknessProperty, new Thickness(1));
            bdr.SetValue(Border.BorderBrushProperty, installed ? new SolidColorBrush(Color.FromArgb(80, 16, 185, 129)) : new SolidColorBrush(AppColors.AccentLight));

            FrameworkElementFactory cp = new FrameworkElementFactory(typeof(ContentPresenter));
            cp.SetValue(ContentPresenter.VerticalAlignmentProperty, VerticalAlignment.Center);
            bdr.AppendChild(cp);
            tpl.VisualTree = bdr;

            btn.Template = tpl;

            if (!installed)
            {
                btn.Click += (s, e) =>
                {
                    btnTxt.Text = "⏳...";
                    btn.IsEnabled = false;

                    ThreadPool.QueueUserWorkItem(delegate
                    {
                        string outName;
                        string err;
                        bool ok = DownloadModrinthJar(slug, GetModsDir(), out outName, out err);

                        Dispatcher.BeginInvoke(new Action(() =>
                        {
                            if (ok)
                            {
                                btnTxt.Text = "✓ Есть";
                                btnTxt.Foreground = new SolidColorBrush(AppColors.AccentGreen);
                                ReloadLocalMods();
                            }
                            else
                            {
                                btnTxt.Text = "Скачать";
                                btn.IsEnabled = true;
                            }
                        }));
                    });
                };
            }

            Grid.SetColumn(btn, 1);
            g.Children.Add(btn);

            b.Child = g;
            return b;
        }

        #endregion

        #region Modrinth Catalog View

        private Grid CreateModrinthView()
        {
            Grid g = new Grid();
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto }); // Search
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto }); // Chips & Status
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(10, GridUnitType.Pixel) }); // Gap
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });   // Cards Grid

            // Search Bar
            Grid searchGrid = new Grid();
            searchGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            searchGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(8, GridUnitType.Pixel) });
            searchGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            Border searchBorder = new Border
            {
                Height = 34,
                CornerRadius = new CornerRadius(7),
                Background = new SolidColorBrush(AppColors.InputBg),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(AppColors.InputBorder),
                Padding = new Thickness(10, 0, 10, 0)
            };

            Grid sInGrid = new Grid();
            sInGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            sInGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            sInGrid.Children.Add(IconHelper.CreateStrokeIcon(SvgIcons.Search, 13, new SolidColorBrush(AppColors.TextMuted), 1.8));

            _txtModrinthSearch = new TextBox
            {
                FontSize = 12,
                Foreground = Brushes.White,
                Background = Brushes.Transparent,
                BorderThickness = new Thickness(0),
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(6, 0, 0, 0)
            };
            _txtModrinthSearch.KeyDown += (s, e) =>
            {
                if (e.Key == Key.Enter)
                {
                    LoadModrinthMods(_txtModrinthSearch.Text.Trim());
                }
            };
            Grid.SetColumn(_txtModrinthSearch, 1);
            sInGrid.Children.Add(_txtModrinthSearch);
            searchBorder.Child = sInGrid;
            Grid.SetColumn(searchBorder, 0);
            searchGrid.Children.Add(searchBorder);

            Button btnSearch = CreateCompactButton(SvgIcons.Search, "Найти", (s, e) =>
            {
                LoadModrinthMods(_txtModrinthSearch.Text.Trim());
            });
            btnSearch.Height = 34;
            Grid.SetColumn(btnSearch, 2);
            searchGrid.Children.Add(btnSearch);

            Grid.SetRow(searchGrid, 0);
            g.Children.Add(searchGrid);

            // Chips & Status
            Grid chipsAndStatus = new Grid { Margin = new Thickness(0, 8, 0, 0) };
            chipsAndStatus.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            chipsAndStatus.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            WrapPanel chips = new WrapPanel();
            chips.Children.Add(CreateFilterChip("Все", () => LoadModrinthMods("")));
            chips.Children.Add(CreateFilterChip("Оптимизация", () => LoadModrinthMods("optimization")));
            chips.Children.Add(CreateFilterChip("Шейдеры", () => LoadModrinthMods("shader")));
            chips.Children.Add(CreateFilterChip("Утилиты", () => LoadModrinthMods("utility")));
            Grid.SetColumn(chips, 0);
            chipsAndStatus.Children.Add(chips);

            _txtModrinthStatus = new TextBlock
            {
                Text = "Каталог Modrinth",
                FontSize = 11,
                Foreground = new SolidColorBrush(AppColors.TextMuted),
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(_txtModrinthStatus, 1);
            chipsAndStatus.Children.Add(_txtModrinthStatus);

            Grid.SetRow(chipsAndStatus, 1);
            g.Children.Add(chipsAndStatus);

            // ScrollViewer Cards Grid
            ScrollViewer sv = new ScrollViewer { VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
            Grid cardsGrid = new Grid();
            cardsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            cardsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(10, GridUnitType.Pixel) });
            cardsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            _modrinthCardsLeft = new StackPanel();
            _modrinthCardsRight = new StackPanel();

            Grid.SetColumn(_modrinthCardsLeft, 0);
            Grid.SetColumn(_modrinthCardsRight, 2);

            cardsGrid.Children.Add(_modrinthCardsLeft);
            cardsGrid.Children.Add(_modrinthCardsRight);

            sv.Content = cardsGrid;
            Grid.SetRow(sv, 3);
            g.Children.Add(sv);

            return g;
        }

        private Border CreateFilterChip(string label, Action onClick)
        {
            Border chip = new Border
            {
                CornerRadius = new CornerRadius(5),
                Background = new SolidColorBrush(Color.FromRgb(18, 23, 34)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(AppColors.BorderSubtle),
                Padding = new Thickness(8, 3, 8, 3),
                Margin = new Thickness(0, 0, 6, 0),
                Cursor = Cursors.Hand
            };
            chip.Child = new TextBlock
            {
                Text = label,
                FontSize = 10.5,
                Foreground = new SolidColorBrush(AppColors.TextSecondary)
            };
            chip.MouseEnter += (s, e) =>
            {
                chip.Background = new SolidColorBrush(Color.FromRgb(26, 33, 48));
                (chip.Child as TextBlock).Foreground = Brushes.White;
            };
            chip.MouseLeave += (s, e) =>
            {
                chip.Background = new SolidColorBrush(Color.FromRgb(18, 23, 34));
                (chip.Child as TextBlock).Foreground = new SolidColorBrush(AppColors.TextSecondary);
            };
            chip.MouseLeftButtonDown += (s, e) => onClick();
            return chip;
        }

        private void LoadModrinthMods(string query)
        {
            _txtModrinthStatus.Text = "Поиск...";
            _modrinthCardsLeft.Children.Clear();
            _modrinthCardsRight.Children.Clear();

            ThreadPool.QueueUserWorkItem(delegate
            {
                List<ModrinthItem> results = FetchModrinth(query);

                Dispatcher.BeginInvoke(new Action(() =>
                {
                    if (results.Count == 0)
                    {
                        _txtModrinthStatus.Text = "Ничего не найдено.";
                        return;
                    }

                    _txtModrinthStatus.Text = "Найдено " + results.Count + " модов для Fabric 1.21.x";

                    for (int i = 0; i < results.Count; i++)
                    {
                        Border card = CreateModrinthCard(results[i]);
                        if (i % 2 == 0) _modrinthCardsLeft.Children.Add(card);
                        else _modrinthCardsRight.Children.Add(card);
                    }
                }));
            });
        }

        private List<ModrinthItem> FetchModrinth(string query)
        {
            List<ModrinthItem> list = new List<ModrinthItem>();
            try
            {
                ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072 | SecurityProtocolType.Tls;
                string url = "";
                if (string.IsNullOrEmpty(query))
                {
                    url = "https://api.modrinth.com/v2/search?facets=[[\"project_type:mod\"],[\"categories:fabric\"]]&index=downloads&limit=24";
                }
                else
                {
                    url = "https://api.modrinth.com/v2/search?query=" + Uri.EscapeDataString(query) + "&facets=[[\"project_type:mod\"],[\"categories:fabric\"]]&limit=24";
                }

                using (WebClient wc = new WebClient { Encoding = Encoding.UTF8 })
                {
                    wc.Headers["User-Agent"] = "RainyDLC-Client/2.1 (contact@rainydlc.fun)";
                    string json = wc.DownloadString(url);

                    JavaScriptSerializer js = new JavaScriptSerializer();
                    var root = js.Deserialize<Dictionary<string, object>>(json);
                    if (root != null && root.ContainsKey("hits"))
                    {
                        var hits = root["hits"] as IEnumerable;
                        if (hits != null)
                        {
                            foreach (object h in hits)
                            {
                                var hit = h as Dictionary<string, object>;
                                if (hit == null) continue;

                                ModrinthItem item = new ModrinthItem();
                                if (hit.ContainsKey("title")) item.Title = Convert.ToString(hit["title"]);
                                if (hit.ContainsKey("slug")) item.Slug = Convert.ToString(hit["slug"]);
                                if (hit.ContainsKey("description")) item.Description = Convert.ToString(hit["description"]);
                                if (hit.ContainsKey("icon_url") && hit["icon_url"] != null) item.IconUrl = Convert.ToString(hit["icon_url"]);
                                if (hit.ContainsKey("author")) item.Author = Convert.ToString(hit["author"]);
                                if (hit.ContainsKey("downloads"))
                                {
                                    int d;
                                    int.TryParse(Convert.ToString(hit["downloads"]), out d);
                                    item.Downloads = d;
                                }
                                list.Add(item);
                            }
                        }
                    }
                }
            }
            catch { }
            return list;
        }

        private Border CreateModrinthCard(ModrinthItem item)
        {
            Border b = new Border
            {
                Height = 68,
                CornerRadius = new CornerRadius(10),
                Background = new SolidColorBrush(AppColors.CardBg),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(AppColors.BorderSubtle),
                Padding = new Thickness(10, 0, 10, 0),
                Margin = new Thickness(0, 0, 0, 8)
            };

            Grid g = new Grid();
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            // Icon
            Border iconBorder = new Border
            {
                Width = 34,
                Height = 34,
                CornerRadius = new CornerRadius(7),
                Background = new SolidColorBrush(Color.FromRgb(22, 28, 42)),
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 10, 0),
                ClipToBounds = true
            };

            if (!string.IsNullOrEmpty(item.IconUrl))
            {
                try
                {
                    Image img = new Image
                    {
                        Width = 34,
                        Height = 34,
                        Stretch = Stretch.UniformToFill
                    };
                    BitmapImage bmp = new BitmapImage();
                    bmp.BeginInit();
                    bmp.UriSource = new Uri(item.IconUrl, UriKind.Absolute);
                    bmp.CacheOption = BitmapCacheOption.OnLoad;
                    bmp.EndInit();
                    img.Source = bmp;
                    iconBorder.Child = img;
                }
                catch
                {
                    iconBorder.Child = IconHelper.CreateStrokeIcon(SvgIcons.Cube, 14, new SolidColorBrush(AppColors.AccentPrimary), 1.8);
                }
            }
            else
            {
                iconBorder.Child = IconHelper.CreateStrokeIcon(SvgIcons.Cube, 14, new SolidColorBrush(AppColors.AccentPrimary), 1.8);
            }
            Grid.SetColumn(iconBorder, 0);
            g.Children.Add(iconBorder);

            // Title & Description
            StackPanel sp = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, 0, 8, 0) };
            StackPanel titleRow = new StackPanel { Orientation = Orientation.Horizontal };
            titleRow.Children.Add(new TextBlock
            {
                Text = item.Title,
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                Foreground = Brushes.White,
                TextTrimming = TextTrimming.CharacterEllipsis,
                MaxWidth = 170
            });
            titleRow.Children.Add(new TextBlock
            {
                Text = "  " + item.FormattedDownloads + " ⬇",
                FontSize = 9.5,
                Foreground = new SolidColorBrush(AppColors.TextMuted),
                VerticalAlignment = VerticalAlignment.Center
            });
            sp.Children.Add(titleRow);

            sp.Children.Add(new TextBlock
            {
                Text = item.Description,
                FontSize = 10,
                Foreground = new SolidColorBrush(AppColors.TextMuted),
                TextTrimming = TextTrimming.CharacterEllipsis,
                Margin = new Thickness(0, 2, 0, 0),
                MaxHeight = 26,
                TextWrapping = TextWrapping.Wrap
            });
            Grid.SetColumn(sp, 1);
            g.Children.Add(sp);

            // Download Button
            string installedFile;
            bool isInstalled = IsModInstalled(item.Slug, item.Title, out installedFile);

            Button btnDl = new Button
            {
                Height = 28,
                Cursor = Cursors.Hand,
                Focusable = false,
                Padding = new Thickness(10, 0, 10, 0),
                VerticalAlignment = VerticalAlignment.Center
            };

            TextBlock btnTxt = new TextBlock
            {
                Text = isInstalled ? "✓ Есть" : "Скачать",
                FontSize = 10.5,
                FontWeight = FontWeights.Medium,
                Foreground = isInstalled ? new SolidColorBrush(AppColors.AccentGreen) : Brushes.White
            };
            btnDl.Content = btnTxt;

            ControlTemplate tpl = new ControlTemplate(typeof(Button));
            FrameworkElementFactory bdr = new FrameworkElementFactory(typeof(Border));
            bdr.Name = "DlBdr";
            bdr.SetValue(Border.CornerRadiusProperty, new CornerRadius(5));
            bdr.SetValue(Border.BackgroundProperty, isInstalled ? new SolidColorBrush(Color.FromArgb(32, 16, 185, 129)) : new SolidColorBrush(AppColors.AccentPrimary));
            bdr.SetValue(Border.BorderThicknessProperty, new Thickness(1));
            bdr.SetValue(Border.BorderBrushProperty, isInstalled ? new SolidColorBrush(Color.FromArgb(80, 16, 185, 129)) : new SolidColorBrush(AppColors.AccentLight));

            FrameworkElementFactory cp = new FrameworkElementFactory(typeof(ContentPresenter));
            cp.SetValue(ContentPresenter.VerticalAlignmentProperty, VerticalAlignment.Center);
            bdr.AppendChild(cp);
            tpl.VisualTree = bdr;

            btnDl.Template = tpl;

            btnDl.Click += (s, e) =>
            {
                string dummy;
                if (IsModInstalled(item.Slug, item.Title, out dummy))
                {
                    System.Windows.MessageBox.Show("Мод '" + item.Title + "' уже установлен.", "Modrinth", MessageBoxButton.OK, MessageBoxImage.Information);
                    return;
                }

                btnTxt.Text = "⏳...";
                btnDl.IsEnabled = false;

                ThreadPool.QueueUserWorkItem(delegate
                {
                    string outName;
                    string err;
                    bool ok = DownloadModrinthJar(item.Slug, GetModsDir(), out outName, out err);

                    Dispatcher.BeginInvoke(new Action(() =>
                    {
                        if (ok)
                        {
                            btnTxt.Text = "✓ Есть";
                            btnTxt.Foreground = new SolidColorBrush(AppColors.AccentGreen);
                            btnDl.IsEnabled = true;
                            AppendLog("[Modrinth] Успешно загружен мод: " + outName);
                        }
                        else
                        {
                            btnTxt.Text = "Скачать";
                            btnDl.IsEnabled = true;
                            AppendLog("[Modrinth] Ошибка: " + err);
                            System.Windows.MessageBox.Show("Не удалось загрузить мод:\n" + err, "Ошибка", MessageBoxButton.OK, MessageBoxImage.Warning);
                        }
                    }));
                });
            };

            Grid.SetColumn(btnDl, 2);
            g.Children.Add(btnDl);

            b.Child = g;
            return b;
        }

        private bool IsModInstalled(string slug, string title, out string installedFile)
        {
            installedFile = "";
            try
            {
                string md = GetModsDir();
                if (!Directory.Exists(md)) return false;

                string[] files = Directory.GetFiles(md, "*.jar*");
                string sClean = slug.Replace("-", "").ToLower();
                string tClean = title.Replace(" ", "").ToLower();

                foreach (string f in files)
                {
                    string name = System.IO.Path.GetFileName(f).ToLower();
                    if (name.Contains(sClean) || (!string.IsNullOrEmpty(tClean) && name.Contains(tClean)))
                    {
                        installedFile = System.IO.Path.GetFileName(f);
                        return true;
                    }
                }
            }
            catch { }
            return false;
        }

        private static bool DownloadModrinthJar(string slug, string modsDir, out string outFileName, out string error)
        {
            outFileName = "";
            error = "";
            try
            {
                ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072 | SecurityProtocolType.Tls;

                string vUrl = "https://api.modrinth.com/v2/project/" + Uri.EscapeDataString(slug) + "/version?loaders=[\"fabric\"]&game_versions=[\"1.21.11\",\"1.21.4\",\"1.21.1\",\"1.21\"]";
                string json = "";

                using (WebClient wc = new WebClient { Encoding = Encoding.UTF8 })
                {
                    wc.Headers["User-Agent"] = "RainyDLC-Client/2.1 (contact@rainydlc.fun)";
                    try { json = wc.DownloadString(vUrl); } catch { }

                    JavaScriptSerializer js = new JavaScriptSerializer();
                    var versions = js.DeserializeObject(json) as IEnumerable;

                    if (versions == null || !versions.GetEnumerator().MoveNext())
                    {
                        string allVUrl = "https://api.modrinth.com/v2/project/" + Uri.EscapeDataString(slug) + "/version?loaders=[\"fabric\"]";
                        json = wc.DownloadString(allVUrl);
                        versions = js.DeserializeObject(json) as IEnumerable;
                    }

                    if (versions != null)
                    {
                        foreach (object vObj in versions)
                        {
                            var vDict = vObj as Dictionary<string, object>;
                            if (vDict == null || !vDict.ContainsKey("files")) continue;

                            var files = vDict["files"] as IEnumerable;
                            if (files == null) continue;

                            foreach (object fObj in files)
                            {
                                var fDict = fObj as Dictionary<string, object>;
                                if (fDict == null) continue;

                                string downloadUrl = Convert.ToString(fDict["url"]);
                                string fileName = Convert.ToString(fDict["filename"]);

                                if (!string.IsNullOrEmpty(downloadUrl) && fileName.EndsWith(".jar", StringComparison.OrdinalIgnoreCase))
                                {
                                    if (!Directory.Exists(modsDir)) Directory.CreateDirectory(modsDir);
                                    string targetPath = System.IO.Path.Combine(modsDir, fileName);

                                    using (WebClient dlClient = new WebClient())
                                    {
                                        dlClient.Headers["User-Agent"] = "RainyDLC-Client/2.1 (contact@rainydlc.fun)";
                                        dlClient.DownloadFile(new Uri(downloadUrl), targetPath);
                                    }

                                    outFileName = fileName;
                                    return true;
                                }
                            }
                        }
                    }
                }
                error = "Не найден подходящий файл для Fabric 1.21";
                return false;
            }
            catch (Exception ex)
            {
                error = ex.Message;
                return false;
            }
        }

        #endregion

        #region Page 3: Консоль (Console Logs)

        private Grid CreatePageConsole()
        {
            Grid g = new Grid();
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });                         // Toolbar
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(8, GridUnitType.Pixel) });  // Gap
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });   // Terminal Box

            // Toolbar
            Border bar = new Border
            {
                Height = 40,
                CornerRadius = new CornerRadius(10),
                Background = new SolidColorBrush(AppColors.CardBg),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(AppColors.BorderSubtle),
                Padding = new Thickness(14, 0, 10, 0)
            };

            Grid bg = new Grid();
            bg.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            bg.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            // macOS Traffic Light Dots + Title
            StackPanel statusPanel = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                VerticalAlignment = VerticalAlignment.Center
            };

            Ellipse dotRed = new Ellipse { Width = 9, Height = 9, Fill = new SolidColorBrush(Color.FromRgb(239, 68, 68)), Margin = new Thickness(0, 0, 5, 0) };
            Ellipse dotYel = new Ellipse { Width = 9, Height = 9, Fill = new SolidColorBrush(Color.FromRgb(245, 158, 11)), Margin = new Thickness(0, 0, 5, 0) };
            Ellipse dotGrn = new Ellipse { Width = 9, Height = 9, Fill = new SolidColorBrush(Color.FromRgb(16, 185, 129)), Margin = new Thickness(0, 0, 10, 0) };

            statusPanel.Children.Add(dotRed);
            statusPanel.Children.Add(dotYel);
            statusPanel.Children.Add(dotGrn);

            _txtConsoleHeaderStatus = new TextBlock
            {
                Text = "Консоль процесса • Ожидание запуска",
                FontSize = 12,
                FontWeight = FontWeights.Medium,
                Foreground = new SolidColorBrush(AppColors.TextSecondary),
                VerticalAlignment = VerticalAlignment.Center
            };
            statusPanel.Children.Add(_txtConsoleHeaderStatus);

            Grid.SetColumn(statusPanel, 0);
            bg.Children.Add(statusPanel);

            StackPanel btns = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };

            Button bClr = CreateCompactButton(SvgIcons.Trash, "Очистить", (s, e) =>
            {
                if (_txtConsoleLogs != null) _txtConsoleLogs.Clear();
            });
            btns.Children.Add(bClr);

            Button bCpy = CreateCompactButton(SvgIcons.Copy, "Копировать", (s, e) =>
            {
                try
                {
                    System.Windows.Clipboard.SetText(_txtConsoleLogs.Text);
                    System.Windows.MessageBox.Show("Лог скопирован.", "Консоль", MessageBoxButton.OK, MessageBoxImage.Information);
                }
                catch { }
            });
            bCpy.Margin = new Thickness(6, 0, 0, 0);
            btns.Children.Add(bCpy);

            Button bLog = CreateCompactButton(SvgIcons.Folder, "latest.log", (s, e) =>
            {
                try
                {
                    string logFile = System.IO.Path.Combine(_projectDir, "run", "logs", "latest.log");
                    if (File.Exists(logFile)) Process.Start("notepad.exe", logFile);
                }
                catch { }
            });
            bLog.Margin = new Thickness(6, 0, 0, 0);
            btns.Children.Add(bLog);

            Grid.SetColumn(btns, 1);
            bg.Children.Add(btns);

            bar.Child = bg;
            Grid.SetRow(bar, 0);
            g.Children.Add(bar);

            // Terminal Box
            Border bdrConsole = new Border
            {
                CornerRadius = new CornerRadius(10),
                Background = new SolidColorBrush(Color.FromRgb(8, 10, 15)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromRgb(20, 25, 38)),
                Padding = new Thickness(12)
            };

            _scrollConsole = new ScrollViewer { VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
            _txtConsoleLogs = new TextBox
            {
                IsReadOnly = true,
                Background = Brushes.Transparent,
                BorderThickness = new Thickness(0),
                Foreground = new SolidColorBrush(Color.FromRgb(203, 213, 225)),
                FontFamily = new FontFamily("Consolas, Courier New"),
                FontSize = 11.5,
                TextWrapping = TextWrapping.Wrap,
                AcceptsReturn = true
            };
            _scrollConsole.Content = _txtConsoleLogs;
            bdrConsole.Child = _scrollConsole;

            Grid.SetRow(bdrConsole, 2);
            g.Children.Add(bdrConsole);

            AppendLog("RainyDLC Launcher v2.1 запущен.");
            AppendLog("Папка проекта: " + _projectDir);
            AppendLog("Память: " + _data.RamMb + " MB");

            return g;
        }

        private void AppendLog(string message)
        {
            Dispatcher.BeginInvoke(new Action(() =>
            {
                if (_txtConsoleLogs != null)
                {
                    string time = DateTime.Now.ToString("HH:mm:ss");
                    _txtConsoleLogs.AppendText("[" + time + "] " + message + Environment.NewLine);
                    if (_scrollConsole != null) _scrollConsole.ScrollToEnd();
                }
            }));
        }

        #endregion

        #region Game Launch & Management

        private void OnLaunchButtonClick()
        {
            if (_isDownloading)
            {
                System.Windows.MessageBox.Show(
                    "Идет скачивание файлов клиента. Дождитесь окончания загрузки.",
                    "RainyDLC",
                    MessageBoxButton.OK,
                    MessageBoxImage.Information
                );
                return;
            }

            if (_runningProcess != null && !_runningProcess.HasExited)
            {
                MessageBoxResult r = System.Windows.MessageBox.Show(
                    "Клиент Minecraft запущен. Завершить процесс?",
                    "RainyDLC",
                    MessageBoxButton.YesNo,
                    MessageBoxImage.Question
                );
                if (r == MessageBoxResult.Yes)
                {
                    StopClient();
                }
                return;
            }

            // If client files are missing and no gradlew exists, automatically download client!
            if (!IsClientInstalled())
            {
                string gradlew = System.IO.Path.Combine(_projectDir, "gradlew.bat");
                if (!File.Exists(gradlew))
                {
                    AppendLog("[Launcher] Файлы клиента не найдены. Запуск автоматического скачивания...");
                    StartClientDownload(true);
                    return;
                }
            }

            StartClient();
        }

        private void StartClient()
        {
            if (_isDownloading)
            {
                System.Windows.MessageBox.Show(
                    "Идет скачивание файлов клиента. Дождитесь окончания загрузки.",
                    "RainyDLC",
                    MessageBoxButton.OK,
                    MessageBoxImage.Information
                );
                return;
            }

            string modsDir = GetModsDir();
            bool hasClientJar = IsClientInstalled();
            string gradlew = System.IO.Path.Combine(_projectDir, "gradlew.bat");
            bool hasGradlew = File.Exists(gradlew);

            // If neither client jar nor gradlew exists, trigger download!
            if (!hasClientJar && !hasGradlew)
            {
                AppendLog("[Launcher] Файлы клиента отсутствуют. Запуск скачивания...");
                StartClientDownload(true);
                return;
            }

            // If client jar is missing in modsDir, check if local build exists in build/libs/
            if (!hasClientJar)
            {
                string localBuild1 = System.IO.Path.Combine(_projectDir, "build", "libs", "rainydlc-1.0-SNAPSHOT.jar");
                string localBuild2 = System.IO.Path.Combine(_projectDir, "build", "libs", "rainydlc-protected.jar");
                string src = File.Exists(localBuild2) ? localBuild2 : (File.Exists(localBuild1) ? localBuild1 : null);
                if (src != null)
                {
                    try
                    {
                        if (!Directory.Exists(modsDir)) Directory.CreateDirectory(modsDir);
                        File.Copy(src, GetClientJarPath(), true);
                        hasClientJar = true;
                        AppendLog("[Launcher] Синхронизирована локальная сборка в: " + GetClientJarPath());
                        UpdateClientStatusUI();
                        ReloadLocalMods();
                    }
                    catch { }
                }
                else if (!hasGradlew)
                {
                    AppendLog("[Launcher] Скачивание файлов клиента...");
                    StartClientDownload(true);
                    return;
                }
            }

            SetLaunchUIStarting();

            _data.LaunchCount++;
            _data.LastLaunchTime = DateTime.Now.ToString("dd MMMM в HH:mm", new System.Globalization.CultureInfo("ru-RU"));
            _data.Save();

            if (_txtHeroLaunches != null) _txtHeroLaunches.Text = _data.LaunchCount + " раз";
            if (_txtHeroLastLaunch != null) _txtHeroLastLaunch.Text = _data.LastLaunchTime;

            _sessionStart = DateTime.Now;
            int initialPlaytime = _data.PlaytimeMinutes;

            if (_playtimeTimer == null)
            {
                _playtimeTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(5) };
                _playtimeTimer.Tick += (s, e) =>
                {
                    if (_runningProcess != null && !_runningProcess.HasExited)
                    {
                        int currentMins = (int)(DateTime.Now - _sessionStart).TotalMinutes;
                        _data.PlaytimeMinutes = initialPlaytime + currentMins;
                        _data.Save();
                        if (_txtHeroPlaytime != null) _txtHeroPlaytime.Text = LauncherData.FormatPlaytime(_data.PlaytimeMinutes);
                    }
                };
            }
            _playtimeTimer.Start();

            string usernameArg = string.IsNullOrEmpty(_data.Username) ? "Owner" : _data.Username.Trim();
            string javaOpts = string.Format("-Dfile.encoding=UTF-8 -Xmx{0}m {1}", _data.RamMb, _data.JvmArgs);

            AppendLog("=========================================");
            AppendLog("Запуск клиента RainyDLC");
            AppendLog("Игрок: " + usernameArg);
            AppendLog("RAM: " + _data.RamMb + " MB");
            AppendLog("Папка модов: " + modsDir);
            AppendLog("=========================================");

            ThreadPool.QueueUserWorkItem(delegate
            {
                try
                {
                    ProcessStartInfo psi = new ProcessStartInfo();

                    if (hasGradlew && (_data.LaunchMode == 1 || (_data.LaunchMode == 0 && !hasClientJar)))
                    {
                        AppendLog("Режим запуска: gradlew.bat runClient");
                        psi.FileName = "cmd.exe";
                        psi.Arguments = "/c \"\"" + gradlew + "\" runClient --args=\"--username " + usernameArg + "\"\"";
                        psi.WorkingDirectory = _projectDir;
                    }
                    else
                    {
                        string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                        string tlBootstrap = System.IO.Path.Combine(appData, ".tlauncher", "legacy", "Minecraft", "launcher", "bootstrap.jar");
                        string javaExe = FindJavaExecutable();

                        if (File.Exists(tlBootstrap))
                        {
                            AppendLog("Режим запуска: установленный Minecraft лаунчер с модом RainyDLC...");
                            psi.FileName = javaExe;
                            psi.Arguments = string.Format("-Xmx{0}m {1} -jar \"{2}\"", _data.RamMb, _data.JvmArgs, tlBootstrap);
                            psi.WorkingDirectory = System.IO.Path.GetDirectoryName(tlBootstrap);
                        }
                        else if (hasGradlew)
                        {
                            AppendLog("Режим запуска: gradlew.bat runClient");
                            psi.FileName = "cmd.exe";
                            psi.Arguments = "/c \"\"" + gradlew + "\" runClient --args=\"--username " + usernameArg + "\"\"";
                            psi.WorkingDirectory = _projectDir;
                        }
                        else
                        {
                            AppendLog("Режим запуска: Java клиент...");
                            psi.FileName = javaExe;
                            psi.Arguments = "-version";
                            psi.WorkingDirectory = modsDir;
                        }
                    }

                    psi.UseShellExecute = false;
                    psi.CreateNoWindow = true;
                    psi.RedirectStandardOutput = true;
                    psi.RedirectStandardError = true;
                    psi.EnvironmentVariables["JAVA_TOOL_OPTIONS"] = javaOpts;

                    Process proc = new Process();
                    proc.StartInfo = psi;
                    proc.EnableRaisingEvents = true;

                    proc.OutputDataReceived += (s, e) =>
                    {
                        if (e.Data != null)
                        {
                            AppendLog(e.Data);
                            if (e.Data.Contains("Setting user:") || e.Data.Contains("Backend library: LWJGL") || e.Data.Contains("Loaded"))
                            {
                                Dispatcher.BeginInvoke(new Action(() =>
                                {
                                    SetLaunchUIRunning(proc.Id);
                                }));
                            }
                        }
                    };

                    proc.ErrorDataReceived += (s, e) =>
                    {
                        if (e.Data != null) AppendLog("[!] " + e.Data);
                    };

                    proc.Exited += (s, e) =>
                    {
                        Dispatcher.BeginInvoke(new Action(() =>
                        {
                            _runningProcess = null;
                            if (_playtimeTimer != null) _playtimeTimer.Stop();
                            int totalElapsedSecs = (int)(DateTime.Now - _sessionStart).TotalSeconds;
                            int sessionMins = totalElapsedSecs / 60;
                            if (sessionMins == 0 && totalElapsedSecs >= 30) sessionMins = 1;

                            _data.PlaytimeMinutes = initialPlaytime + sessionMins;
                            _data.Save();

                            if (_txtHeroPlaytime != null) _txtHeroPlaytime.Text = LauncherData.FormatPlaytime(_data.PlaytimeMinutes);
                            SetLaunchUIIdle();
                            AppendLog("Клиент завершил работу.");
                        }));
                    };

                    proc.Start();
                    proc.BeginOutputReadLine();
                    proc.BeginErrorReadLine();
                    _runningProcess = proc;

                    Dispatcher.BeginInvoke(new Action(() =>
                    {
                        SetLaunchUIRunning(proc.Id);
                    }));
                }
                catch (Exception ex)
                {
                    Dispatcher.BeginInvoke(new Action(() =>
                    {
                        AppendLog("[!] Ошибка: " + ex.Message);
                        SetLaunchUIIdle();
                        System.Windows.MessageBox.Show("Не удалось запустить клиент:\n" + ex.Message, "Ошибка запуска", MessageBoxButton.OK, MessageBoxImage.Error);
                    }));
                }
            });
        }

        private string FindJavaExecutable()
        {
            string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (!string.IsNullOrEmpty(javaHome))
            {
                string p = System.IO.Path.Combine(javaHome, "bin", "java.exe");
                if (File.Exists(p)) return p;
            }

            string userProfile = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
            string jdks = System.IO.Path.Combine(userProfile, ".jdks");
            if (Directory.Exists(jdks))
            {
                try
                {
                    string[] corr = Directory.GetFiles(jdks, "java.exe", SearchOption.AllDirectories);
                    if (corr.Length > 0) return corr[corr.Length - 1];
                }
                catch { }
            }

            string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            string tlJre = System.IO.Path.Combine(appData, ".tlauncher", "mojang_jre");
            if (Directory.Exists(tlJre))
            {
                try
                {
                    string[] found = Directory.GetFiles(tlJre, "java.exe", SearchOption.AllDirectories);
                    if (found.Length > 0) return found[0];
                }
                catch { }
            }

            return "java.exe";
        }

        #region Client Downloader Engine

        private void StartClientDownload(bool autoLaunchAfter)
        {
            if (_isDownloading) return;

            _isDownloading = true;
            _autoLaunchAfterDownload = autoLaunchAfter;
            _downloadStartTime = DateTime.Now;

            string modsDir = GetModsDir();
            if (!Directory.Exists(modsDir))
            {
                try { Directory.CreateDirectory(modsDir); } catch { }
            }

            SetDownloadUIActive("Подготовка к скачиванию...", 0);
            AppendLog("=========================================");
            AppendLog("[Downloader] Старт скачивания файлов клиента RainyDLC");
            AppendLog("[Downloader] Папка назначения модов: " + modsDir);
            AppendLog("=========================================");

            SwitchPage(0);

            ThreadPool.QueueUserWorkItem(delegate
            {
                try
                {
                    string targetJar = System.IO.Path.Combine(modsDir, "rainydlc.jar");
                    string tempJar = targetJar + ".download";

                    string localBuild1 = System.IO.Path.Combine(_projectDir, "build", "libs", "rainydlc-1.0-SNAPSHOT.jar");
                    string localBuild2 = System.IO.Path.Combine(_projectDir, "build", "libs", "rainydlc-protected.jar");
                    string sourceLocal = File.Exists(localBuild2) ? localBuild2 : (File.Exists(localBuild1) ? localBuild1 : null);

                    string url = string.IsNullOrEmpty(_data.ClientDownloadUrl)
                        ? "https://github.com/RainyDLC/FrostixDLC/releases/latest/download/rainydlc.jar"
                        : _data.ClientDownloadUrl.Trim();

                    bool downloadedSuccessfully = false;

                    AppendLog("[Downloader] Скачивание клиента: " + url);
                    Dispatcher.BeginInvoke(new Action(() =>
                    {
                        SetDownloadUIActive("Скачивание rainydlc.jar...", 0);
                    }));

                    try
                    {
                        using (WebClient wc = new WebClient())
                        {
                            _activeDownloader = wc;
                            wc.Headers["User-Agent"] = "RainyDLC-Launcher/2.1";

                            DateTime lastUpdate = DateTime.MinValue;
                            wc.DownloadProgressChanged += (s, e) =>
                            {
                                if ((DateTime.Now - lastUpdate).TotalMilliseconds > 100)
                                {
                                    lastUpdate = DateTime.Now;
                                    double speedMb = 0;
                                    double elapsed = (DateTime.Now - _downloadStartTime).TotalSeconds;
                                    if (elapsed > 0) speedMb = (e.BytesReceived / 1048576.0) / elapsed;

                                    string detail = string.Format(
                                        "{0:F1} МБ / {1:F1} МБ ({2}%) • {3:F1} МБ/с",
                                        e.BytesReceived / 1048576.0,
                                        e.TotalBytesToReceive / 1048576.0,
                                        e.ProgressPercentage,
                                        speedMb
                                    );

                                    Dispatcher.BeginInvoke(new Action(() =>
                                    {
                                        SetDownloadProgress(e.ProgressPercentage, "Скачивание rainydlc.jar...", detail);
                                    }));
                                }
                            };

                            AutoResetEvent done = new AutoResetEvent(false);
                            Exception dlEx = null;
                            wc.DownloadFileCompleted += (s, e) =>
                            {
                                if (e.Error != null) dlEx = e.Error;
                                done.Set();
                            };

                            wc.DownloadFileAsync(new Uri(url), tempJar);
                            done.WaitOne();

                            if (dlEx != null) throw dlEx;

                            if (File.Exists(tempJar) && new FileInfo(tempJar).Length > 1024)
                            {
                                if (File.Exists(targetJar)) File.Delete(targetJar);
                                File.Move(tempJar, targetJar);
                                downloadedSuccessfully = true;
                                AppendLog("[Downloader] rainydlc.jar успешно загружен (" + FormatFileSize(new FileInfo(targetJar).Length) + ")");
                            }
                        }
                    }
                    catch (Exception ex)
                    {
                        AppendLog("[Downloader] Предупреждение сетевой загрузки: " + ex.Message);
                        if (File.Exists(tempJar)) { try { File.Delete(tempJar); } catch { } }

                        if (sourceLocal != null && File.Exists(sourceLocal))
                        {
                            AppendLog("[Downloader] Использована локальная копия из " + sourceLocal);
                            Dispatcher.BeginInvoke(new Action(() =>
                            {
                                SetDownloadProgress(75, "Копирование локальной сборки клиента...", "Синхронизация файлов...");
                            }));
                            File.Copy(sourceLocal, targetJar, true);
                            downloadedSuccessfully = true;
                        }
                    }

                    if (!downloadedSuccessfully)
                    {
                        throw new Exception("Не удалось скачать файлы клиента (проверьте интернет-соединение или URL в Настройках)");
                    }

                    EnsureFabricApiInstalled(modsDir);

                    Dispatcher.BeginInvoke(new Action(() =>
                    {
                        _isDownloading = false;
                        _activeDownloader = null;
                        SetDownloadUICompleted();
                        UpdateClientStatusUI();
                        ReloadLocalMods();

                        AppendLog("[Downloader] Все файлы клиента готовы к игре!");

                        if (_autoLaunchAfterDownload)
                        {
                            _autoLaunchAfterDownload = false;
                            StartClient();
                        }
                    }));
                }
                catch (Exception ex)
                {
                    Dispatcher.BeginInvoke(new Action(() =>
                    {
                        _isDownloading = false;
                        _activeDownloader = null;
                        SetDownloadUIIdle();
                        UpdateClientStatusUI();
                        AppendLog("[!] Ошибка скачивания клиента: " + ex.Message);
                        System.Windows.MessageBox.Show(
                            "Не удалось скачать файлы клиента:\n" + ex.Message,
                            "Ошибка загрузки",
                            MessageBoxButton.OK,
                            MessageBoxImage.Error
                        );
                    }));
                }
            });
        }

        private void EnsureFabricApiInstalled(string modsDir)
        {
            try
            {
                string[] existing = Directory.GetFiles(modsDir, "*fabric*api*.jar");
                if (existing.Length > 0)
                {
                    AppendLog("[Downloader] Fabric API уже установлен: " + System.IO.Path.GetFileName(existing[0]));
                    return;
                }

                string fApiUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.141.3+1.21.11/fabric-api-0.141.3+1.21.11.jar";
                string fApiTarget = System.IO.Path.Combine(modsDir, "fabric-api-0.141.3+1.21.11.jar");
                string fApiTemp = fApiTarget + ".download";

                AppendLog("[Downloader] Скачивание Fabric API 0.141.3...");
                Dispatcher.BeginInvoke(new Action(() =>
                {
                    SetDownloadProgress(90, "Скачивание Fabric API...", "Загрузка вспомогательных библиотек...");
                }));

                using (WebClient wc = new WebClient())
                {
                    wc.Headers["User-Agent"] = "RainyDLC-Launcher/2.1";
                    wc.DownloadFile(new Uri(fApiUrl), fApiTemp);
                    if (File.Exists(fApiTemp) && new FileInfo(fApiTemp).Length > 1024)
                    {
                        if (File.Exists(fApiTarget)) File.Delete(fApiTarget);
                        File.Move(fApiTemp, fApiTarget);
                        AppendLog("[Downloader] Fabric API успешно установлен (" + FormatFileSize(new FileInfo(fApiTarget).Length) + ")");
                    }
                }
            }
            catch (Exception ex)
            {
                AppendLog("[Downloader] Предупреждение при загрузке Fabric API: " + ex.Message);
            }
        }

        private void CancelClientDownload()
        {
            if (_activeDownloader != null)
            {
                try
                {
                    _activeDownloader.CancelAsync();
                    _activeDownloader.Dispose();
                }
                catch { }
                _activeDownloader = null;
            }
            _isDownloading = false;
            _autoLaunchAfterDownload = false;
            AppendLog("[Downloader] Скачивание отменено пользователем.");
            SetDownloadUIIdle();
        }

        private void SetDownloadUIActive(string status, double percent)
        {
            if (_downloadBanner != null) _downloadBanner.Visibility = Visibility.Visible;
            if (_downloadProgressBar != null) _downloadProgressBar.Value = percent;
            if (_txtDownloadStatus != null) _txtDownloadStatus.Text = status;
            if (_txtDownloadDetails != null) _txtDownloadDetails.Text = "Подключение к серверу...";

            if (_btnDockLaunchText != null)
            {
                _btnDockLaunchText.Text = string.Format("СКАЧИВАНИЕ {0}%", (int)percent);
            }
            if (_btnDockLaunchIcon != null)
            {
                _btnDockLaunchIcon.Data = Geometry.Parse(SvgIcons.Download);
            }

            Border bdr = _btnDockLaunch.Template.FindName("DockLaunchBdr", _btnDockLaunch) as Border;
            if (bdr != null)
            {
                bdr.Background = new SolidColorBrush(AppColors.AccentAmber);
                bdr.BorderBrush = new SolidColorBrush(Color.FromRgb(252, 211, 77));
            }

            if (_dockStatusDot != null) _dockStatusDot.Fill = new SolidColorBrush(AppColors.AccentAmber);
            if (_txtDockStatus != null) _txtDockStatus.Text = "Скачивание файлов...";
        }

        private void SetDownloadProgress(double percent, string status, string details)
        {
            if (_downloadBanner != null) _downloadBanner.Visibility = Visibility.Visible;
            if (_downloadProgressBar != null) _downloadProgressBar.Value = percent;
            if (_txtDownloadStatus != null) _txtDownloadStatus.Text = status;
            if (_txtDownloadDetails != null) _txtDownloadDetails.Text = details;

            if (_btnDockLaunchText != null)
            {
                _btnDockLaunchText.Text = string.Format("СКАЧИВАНИЕ {0}%", (int)percent);
            }
            if (_txtDockStatus != null) _txtDockStatus.Text = "Загрузка: " + (int)percent + "%";
        }

        private void SetDownloadUICompleted()
        {
            if (_downloadProgressBar != null) _downloadProgressBar.Value = 100;
            if (_txtDownloadStatus != null)
            {
                _txtDownloadStatus.Text = "✓ Готово";
                _txtDownloadStatus.Foreground = new SolidColorBrush(AppColors.AccentGreen);
            }
            if (_txtDownloadDetails != null)
            {
                _txtDownloadDetails.Text = "Все файлы клиента успешно загружены и установлены!";
                _txtDownloadDetails.Foreground = new SolidColorBrush(AppColors.AccentGreen);
            }

            DispatcherTimer t = new DispatcherTimer { Interval = TimeSpan.FromSeconds(3) };
            t.Tick += (s, e) =>
            {
                t.Stop();
                if (!_isDownloading && _downloadBanner != null)
                {
                    _downloadBanner.Visibility = Visibility.Collapsed;
                }
            };
            t.Start();

            SetLaunchUIIdle();
            UpdateClientStatusUI();
        }

        private void SetDownloadUIIdle()
        {
            if (_downloadBanner != null) _downloadBanner.Visibility = Visibility.Collapsed;
            SetLaunchUIIdle();
            UpdateClientStatusUI();
        }

        private void UpdateClientStatusUI()
        {
            bool installed = IsClientInstalled();
            long size = GetClientFileSize();

            if (_txtHeroClientStatus != null)
            {
                if (installed)
                {
                    _txtHeroClientStatus.Text = string.Format("Установлен ({0}) ✓", FormatFileSize(size));
                    _txtHeroClientStatus.Foreground = new SolidColorBrush(AppColors.AccentGreen);
                }
                else
                {
                    _txtHeroClientStatus.Text = "Не скачан ✗";
                    _txtHeroClientStatus.Foreground = new SolidColorBrush(AppColors.AccentAmber);
                }
            }

            if (_btnHeroDownload != null)
            {
                StackPanel sp = _btnHeroDownload.Content as StackPanel;
                if (sp != null && sp.Children.Count > 1)
                {
                    TextBlock tb = sp.Children[1] as TextBlock;
                    if (tb != null)
                    {
                        tb.Text = installed ? "Обновить файлы" : "Скачать клиент";
                    }
                }
            }

            if (_btnDockLaunchText != null && !_isDownloading && (_runningProcess == null || _runningProcess.HasExited))
            {
                _btnDockLaunchText.Text = installed ? "ИГРАТЬ" : "СКАЧАТЬ И ИГРАТЬ";
            }
            if (_btnDockLaunchIcon != null && !_isDownloading && (_runningProcess == null || _runningProcess.HasExited))
            {
                _btnDockLaunchIcon.Data = Geometry.Parse(installed ? SvgIcons.Play : SvgIcons.Download);
            }

            if (_txtSettingsClientStatus != null)
            {
                if (installed)
                {
                    _txtSettingsClientStatus.Text = string.Format("Файлы клиента установлены: {0} ({1})", System.IO.Path.GetFileName(GetClientJarPath()), FormatFileSize(size));
                    _txtSettingsClientStatus.Foreground = new SolidColorBrush(AppColors.AccentGreen);
                }
                else
                {
                    _txtSettingsClientStatus.Text = "Файлы клиента не найдены в папке модов";
                    _txtSettingsClientStatus.Foreground = new SolidColorBrush(AppColors.AccentAmber);
                }
            }
        }

        #endregion

        private void SetLaunchUIStarting()
        {
            if (_btnDockLaunchText != null) _btnDockLaunchText.Text = "ЗАПУСК...";
            if (_btnDockLaunchIcon != null) _btnDockLaunchIcon.Data = Geometry.Parse(SvgIcons.Refresh);

            Border bdr = _btnDockLaunch.Template.FindName("DockLaunchBdr", _btnDockLaunch) as Border;
            if (bdr != null)
            {
                bdr.Background = new SolidColorBrush(AppColors.AccentAmber);
                bdr.BorderBrush = new SolidColorBrush(Color.FromRgb(251, 191, 36));
            }

            if (_dockStatusDot != null) _dockStatusDot.Fill = new SolidColorBrush(AppColors.AccentAmber);
            if (_txtDockStatus != null) _txtDockStatus.Text = "Запуск игры...";
            if (_txtConsoleHeaderStatus != null) _txtConsoleHeaderStatus.Text = "Консоль процесса • Запуск...";
        }

        private void SetLaunchUIRunning(int pid)
        {
            if (_btnDockLaunchText != null) _btnDockLaunchText.Text = "ОСТАНОВИТЬ";
            if (_btnDockLaunchIcon != null) _btnDockLaunchIcon.Data = Geometry.Parse(SvgIcons.Stop);

            Border bdr = _btnDockLaunch.Template.FindName("DockLaunchBdr", _btnDockLaunch) as Border;
            if (bdr != null)
            {
                bdr.Background = new SolidColorBrush(AppColors.AccentRed);
                bdr.BorderBrush = new SolidColorBrush(Color.FromRgb(248, 113, 113));
            }

            if (_dockStatusDot != null) _dockStatusDot.Fill = new SolidColorBrush(AppColors.AccentLight);
            if (_txtDockStatus != null) _txtDockStatus.Text = "В игре (PID " + pid + ")";
            if (_txtConsoleHeaderStatus != null) _txtConsoleHeaderStatus.Text = "Консоль процесса • В игре (PID: " + pid + ")";
        }

        private void SetLaunchUIIdle()
        {
            bool installed = IsClientInstalled();

            if (_btnDockLaunchText != null)
            {
                _btnDockLaunchText.Text = installed ? "ИГРАТЬ" : "СКАЧАТЬ И ИГРАТЬ";
            }
            if (_btnDockLaunchIcon != null)
            {
                _btnDockLaunchIcon.Data = Geometry.Parse(installed ? SvgIcons.Play : SvgIcons.Download);
            }

            Border bdr = _btnDockLaunch.Template.FindName("DockLaunchBdr", _btnDockLaunch) as Border;
            if (bdr != null)
            {
                bdr.Background = new SolidColorBrush(AppColors.AccentPrimary);
                bdr.BorderBrush = new SolidColorBrush(AppColors.AccentLight);
            }

            if (_dockStatusDot != null)
            {
                _dockStatusDot.Fill = new SolidColorBrush(installed ? AppColors.AccentGreen : AppColors.AccentAmber);
            }
            if (_txtDockStatus != null)
            {
                _txtDockStatus.Text = installed ? "В сети" : "Клиент не скачан";
            }
            if (_txtConsoleHeaderStatus != null) _txtConsoleHeaderStatus.Text = "Консоль процесса • Ожидание запуска";
        }

        private void StopClient()
        {
            try
            {
                if (_runningProcess != null && !_runningProcess.HasExited)
                {
                    ProcessStartInfo k = new ProcessStartInfo("taskkill", "/F /T /PID " + _runningProcess.Id);
                    k.CreateNoWindow = true;
                    k.UseShellExecute = false;
                    Process.Start(k);
                }
            }
            catch { }
        }

        #endregion

        #region Helpers & Directory Resolution

        private string GetShortProjectPath(string path)
        {
            if (string.IsNullOrEmpty(path)) return "";
            if (path.Length > 28)
            {
                return "..." + path.Substring(path.Length - 25);
            }
            return path;
        }

        private string GetModsDir()
        {
            if (!string.IsNullOrEmpty(_data.CustomGamePath) && Directory.Exists(_data.CustomGamePath))
            {
                string p = System.IO.Path.Combine(_data.CustomGamePath, "mods");
                if (!Directory.Exists(p)) { try { Directory.CreateDirectory(p); } catch { } }
                return p;
            }

            string runMods = System.IO.Path.Combine(_projectDir, "run", "mods");
            if (Directory.Exists(runMods) && File.Exists(System.IO.Path.Combine(_projectDir, "gradlew.bat")))
            {
                return runMods;
            }

            string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            string tlGame = System.IO.Path.Combine(appData, ".tlauncher", "legacy", "Minecraft", "game");
            if (Directory.Exists(tlGame))
            {
                string tlMods = System.IO.Path.Combine(tlGame, "mods");
                if (!Directory.Exists(tlMods)) { try { Directory.CreateDirectory(tlMods); } catch { } }
                return tlMods;
            }

            string mcDir = System.IO.Path.Combine(appData, ".minecraft");
            if (Directory.Exists(mcDir))
            {
                string mcMods = System.IO.Path.Combine(mcDir, "mods");
                if (!Directory.Exists(mcMods)) { try { Directory.CreateDirectory(mcMods); } catch { } }
                return mcMods;
            }

            if (!Directory.Exists(runMods))
            {
                try { Directory.CreateDirectory(runMods); } catch { }
            }
            return runMods;
        }

        private string GetClientJarPath()
        {
            return System.IO.Path.Combine(GetModsDir(), "rainydlc.jar");
        }

        private bool IsClientInstalled()
        {
            try
            {
                string p = GetClientJarPath();
                if (File.Exists(p)) return true;
                string dir = GetModsDir();
                if (Directory.Exists(dir))
                {
                    string[] jars = Directory.GetFiles(dir, "rainydlc*.jar");
                    if (jars.Length > 0) return true;
                }
            }
            catch { }
            return false;
        }

        private long GetClientFileSize()
        {
            try
            {
                string p = GetClientJarPath();
                if (File.Exists(p)) return new FileInfo(p).Length;
                string dir = GetModsDir();
                if (Directory.Exists(dir))
                {
                    string[] jars = Directory.GetFiles(dir, "rainydlc*.jar");
                    if (jars.Length > 0) return new FileInfo(jars[0]).Length;
                }
            }
            catch { }
            return 0;
        }

        private static string FormatFileSize(long bytes)
        {
            if (bytes <= 0) return "0 Б";
            if (bytes >= 1048576)
            {
                return string.Format("{0:F1} МБ", bytes / 1048576.0);
            }
            if (bytes >= 1024)
            {
                return string.Format("{0:F0} КБ", bytes / 1024.0);
            }
            return bytes + " Б";
        }

        private string ResolveProjectDir()
        {
            if (!string.IsNullOrEmpty(_data.CustomProjectPath) && IsValidProjectDir(_data.CustomProjectPath))
            {
                return _data.CustomProjectPath;
            }

            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            if (!string.IsNullOrEmpty(baseDir)) baseDir = baseDir.TrimEnd('\\', '/');

            if (IsValidProjectDir(baseDir)) return baseDir;

            try
            {
                string cd = Directory.GetCurrentDirectory();
                if (IsValidProjectDir(cd)) return cd;
            }
            catch { }

            if (!string.IsNullOrEmpty(baseDir))
            {
                string sub = System.IO.Path.Combine(baseDir, "RainyDLC");
                if (IsValidProjectDir(sub)) return sub;
            }

            try
            {
                string desk = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Desktop), "RainyDLC");
                if (IsValidProjectDir(desk)) return desk;
            }
            catch { }

            try
            {
                string appData = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "RainyDLC", "project_path.txt");
                if (File.Exists(appData))
                {
                    string p = File.ReadAllText(appData).Trim();
                    if (IsValidProjectDir(p)) return p;
                }
            }
            catch { }

            return baseDir;
        }

        private static bool IsValidProjectDir(string path)
        {
            try
            {
                if (string.IsNullOrEmpty(path) || !Directory.Exists(path)) return false;
                if (!File.Exists(System.IO.Path.Combine(path, "gradlew.bat"))) return false;
                if (!File.Exists(System.IO.Path.Combine(path, "build.gradle"))) return false;
                return true;
            }
            catch { return false; }
        }

        private void CloseWindow()
        {
            if (_runningProcess != null && !_runningProcess.HasExited)
            {
                MessageBoxResult r = System.Windows.MessageBox.Show(
                    "Клиент Minecraft всё ещё работает. Закрыть лаунчер и остановить клиент?",
                    "RainyDLC",
                    MessageBoxButton.YesNo,
                    MessageBoxImage.Question
                );
                if (r == MessageBoxResult.Yes)
                {
                    StopClient();
                    Close();
                }
            }
            else
            {
                Close();
            }
        }

        #endregion

        [STAThread]
        public static void Main(string[] args)
        {
            AppDomain.CurrentDomain.UnhandledException += (s, e) =>
            {
                try { File.WriteAllText("launcher_crash.txt", e.ExceptionObject.ToString()); } catch { }
            };

            Application app = new Application();
            app.DispatcherUnhandledException += (s, e) =>
            {
                try { File.WriteAllText("launcher_crash.txt", e.Exception.ToString()); } catch { }
            };

            MainWindow w = new MainWindow();
            app.Run(w);
        }
    }
}
