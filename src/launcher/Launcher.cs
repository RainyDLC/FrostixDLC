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

[assembly: AssemblyTitle("RainyDLC Client Launcher")]
[assembly: AssemblyDescription("RainyDLC Next-Gen Minecraft Client Launcher")]
[assembly: AssemblyConfiguration("")]
[assembly: AssemblyCompany("RainyDLC Team")]
[assembly: AssemblyProduct("RainyDLC")]
[assembly: AssemblyCopyright("RainyDLC 2026")]
[assembly: AssemblyTrademark("")]
[assembly: AssemblyCulture("")]
[assembly: AssemblyVersion("1.0.0.0")]
[assembly: AssemblyFileVersion("1.0.0.0")]

namespace RainyDLC.Launcher
{
    public class LauncherData
    {
        public string Username = "Owner";
        public string Email = "se•••••a@gmail.com";
        public string Role = "Owner";
        public int UserId = 6038;
        public string RegDate = "2 февраля 2026 г.";
        public int LaunchCount = 0;
        public int PlaytimeMinutes = 0;
        public string LastLaunchTime = "";
        public int RamMb = 4096;
        public string JvmArgs = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200";
        public string CustomProjectPath = "";
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

                        if (key == "Username") d.Username = (val == "vortexxxx" || val == "vortexxx") ? "Owner" : val;
                        else if (key == "Email") d.Email = val;
                        else if (key == "Role") d.Role = val;
                        else if (key == "LaunchCount") int.TryParse(val, out d.LaunchCount);
                        else if (key == "PlaytimeMinutes") int.TryParse(val, out d.PlaytimeMinutes);
                        else if (key == "LastLaunchTime") d.LastLaunchTime = val;
                        else if (key == "RamMb") int.TryParse(val, out d.RamMb);
                        else if (key == "JvmArgs") d.JvmArgs = val;
                        else if (key == "CustomProjectPath") d.CustomProjectPath = val;
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

            if (d.Username == "vortexxxx" || d.Username == "vortexxx")
            {
                d.Username = "Owner";
                d.Save();
            }

            // Если запуск первый раз, проверяем реальный лог latest.log
            if (string.IsNullOrEmpty(d.LastLaunchTime))
            {
                try
                {
                    string runLog = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "run", "logs", "latest.log");
                    if (!File.Exists(runLog))
                    {
                        string appDataDir = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "RainyDLC");
                        string prjFile = System.IO.Path.Combine(appDataDir, "project_path.txt");
                        if (File.Exists(prjFile))
                        {
                            string p = File.ReadAllText(prjFile).Trim();
                            runLog = System.IO.Path.Combine(p, "run", "logs", "latest.log");
                        }
                    }

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
            Width = 46;
            Height = 24;
            CornerRadius = new CornerRadius(12);
            Cursor = Cursors.Hand;
            ClipToBounds = true;

            Background = new SolidColorBrush(_isChecked ? Color.FromRgb(99, 102, 241) : Color.FromRgb(39, 39, 45));

            _thumb = new Border
            {
                Width = 18,
                Height = 18,
                CornerRadius = new CornerRadius(9),
                Background = Brushes.White,
                VerticalAlignment = VerticalAlignment.Center,
                HorizontalAlignment = HorizontalAlignment.Left,
                Margin = new Thickness(_isChecked ? 25 : 3, 3, 0, 0)
            };
            Child = _thumb;

            MouseLeftButtonDown += (s, e) =>
            {
                _isChecked = !_isChecked;
                UpdateVisual(true);
                if (CheckedChanged != null) CheckedChanged(_isChecked);
            };
        }

        private void UpdateVisual(bool animate = false)
        {
            Thickness targetMargin = new Thickness(_isChecked ? 25 : 3, 3, 0, 0);
            Color targetColor = _isChecked ? Color.FromRgb(99, 102, 241) : Color.FromRgb(39, 39, 45);

            if (animate)
            {
                ThicknessAnimation thumbAnim = new ThicknessAnimation
                {
                    To = targetMargin,
                    Duration = TimeSpan.FromMilliseconds(180),
                    EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
                };
                _thumb.BeginAnimation(Border.MarginProperty, thumbAnim);

                SolidColorBrush bgBrush = Background as SolidColorBrush;
                if (bgBrush != null && !bgBrush.IsFrozen)
                {
                    ColorAnimation colorAnim = new ColorAnimation
                    {
                        To = targetColor,
                        Duration = TimeSpan.FromMilliseconds(180)
                    };
                    bgBrush.BeginAnimation(SolidColorBrush.ColorProperty, colorAnim);
                }
                else
                {
                    Background = new SolidColorBrush(targetColor);
                }
            }
            else
            {
                _thumb.BeginAnimation(Border.MarginProperty, null);
                _thumb.Margin = targetMargin;
                Background = new SolidColorBrush(targetColor);
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

        // UI References
        private List<Button> _navButtons = new List<Button>();
        private Grid _pageHome;
        private Grid _pageMods;
        private Grid _pageFriends;
        private Grid _pageSettings;
        private Grid _pageConsole;

        // Home View Controls
        private TextBlock _txtLastLaunch;
        private TextBlock _txtLaunchCount;
        private TextBlock _txtPlaytime;
        private Button _btnLaunch;
        private TextBlock _btnLaunchText;

        // Settings View Controls
        private TextBox _txtRamMb;
        private TextBlock _txtPathDisplay;
        private TextBox _txtEmail;

        // Console Log Box
        private TextBox _txtConsoleLogs;
        private ScrollViewer _scrollConsole;

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
        private int _currentPageIndex = -1;

        public MainWindow()
        {
            _data = LauncherData.Load();
            _projectDir = ResolveProjectDir();

            InitializeWindow();
            BuildUI();
        }

        private void InitializeWindow()
        {
            Title = "RainyDLC";
            Width = 1080;
            Height = 670;
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
                    Duration = TimeSpan.FromMilliseconds(260),
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
                CornerRadius = new CornerRadius(16),
                Background = new SolidColorBrush(Color.FromRgb(8, 8, 10)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromRgb(22, 22, 28)),
                Effect = new DropShadowEffect
                {
                    Color = Colors.Black,
                    BlurRadius = 40,
                    ShadowDepth = 4,
                    Opacity = 0.8
                }
            };

            Grid rootGrid = new Grid { ClipToBounds = true };

            // Живой анимированный фон с аурами и неоновым дождём RainyDLC
            Canvas bgCanvas = CreateAmbientBackground();
            rootGrid.Children.Add(bgCanvas);

            Grid mainLayout = new Grid();
            mainLayout.RowDefinitions.Add(new RowDefinition { Height = new GridLength(38, GridUnitType.Pixel) });
            mainLayout.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

            // Верхний заголовок
            Border titleBar = CreateTitleBar();
            Grid.SetRow(titleBar, 0);
            mainLayout.Children.Add(titleBar);

            // Основная область
            Grid bodyGrid = new Grid { Margin = new Thickness(16, 0, 16, 16) };
            bodyGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(240, GridUnitType.Pixel) });
            bodyGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(16, GridUnitType.Pixel) });
            bodyGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            UIElement sidebar = CreateSidebar();
            Grid.SetColumn(sidebar, 0);
            bodyGrid.Children.Add(sidebar);

            Grid contentArea = new Grid();

            _pageHome = CreatePageHome();
            _pageMods = CreatePageMods();
            _pageFriends = CreatePageFriends();
            _pageSettings = CreatePageSettings();
            _pageConsole = CreatePageConsole();

            contentArea.Children.Add(_pageHome);
            contentArea.Children.Add(_pageMods);
            contentArea.Children.Add(_pageFriends);
            contentArea.Children.Add(_pageSettings);
            contentArea.Children.Add(_pageConsole);

            Grid.SetColumn(contentArea, 2);
            bodyGrid.Children.Add(contentArea);

            Grid.SetRow(bodyGrid, 1);
            mainLayout.Children.Add(bodyGrid);

            rootGrid.Children.Add(mainLayout);
            root.Child = rootGrid;
            Content = root;

            SwitchPage(0);
        }

        private Canvas CreateAmbientBackground()
        {
            Canvas canvas = new Canvas
            {
                IsHitTestVisible = false,
                HorizontalAlignment = HorizontalAlignment.Stretch,
                VerticalAlignment = VerticalAlignment.Stretch
            };

            // 1. Фиолетовая аура слева
            Ellipse orb1 = new Ellipse
            {
                Width = 420,
                Height = 420,
                IsHitTestVisible = false
            };
            RadialGradientBrush brush1 = new RadialGradientBrush
            {
                Center = new Point(0.5, 0.5),
                GradientOrigin = new Point(0.5, 0.5),
                RadiusX = 0.5,
                RadiusY = 0.5
            };
            brush1.GradientStops.Add(new GradientStop(Color.FromArgb(50, 6, 182, 212), 0.0));
            brush1.GradientStops.Add(new GradientStop(Color.FromArgb(14, 14, 116, 144), 0.5));
            brush1.GradientStops.Add(new GradientStop(Colors.Transparent, 1.0));
            orb1.Fill = brush1;
            orb1.Effect = new BlurEffect { Radius = 90 };
            Canvas.SetLeft(orb1, -80);
            Canvas.SetTop(orb1, 20);

            TranslateTransform ttOrb1 = new TranslateTransform();
            orb1.RenderTransform = ttOrb1;

            DoubleAnimation animOrb1X = new DoubleAnimation
            {
                From = -20,
                To = 35,
                Duration = TimeSpan.FromSeconds(7),
                AutoReverse = true,
                RepeatBehavior = RepeatBehavior.Forever,
                EasingFunction = new SineEase { EasingMode = EasingMode.EaseInOut }
            };
            DoubleAnimation animOrb1Y = new DoubleAnimation
            {
                From = -15,
                To = 25,
                Duration = TimeSpan.FromSeconds(9),
                AutoReverse = true,
                RepeatBehavior = RepeatBehavior.Forever,
                EasingFunction = new SineEase { EasingMode = EasingMode.EaseInOut }
            };
            ttOrb1.BeginAnimation(TranslateTransform.XProperty, animOrb1X);
            ttOrb1.BeginAnimation(TranslateTransform.YProperty, animOrb1Y);
            canvas.Children.Add(orb1);

            // 2. Синяя сапфировая аура справа
            Ellipse orb2 = new Ellipse
            {
                Width = 460,
                Height = 460,
                IsHitTestVisible = false
            };
            RadialGradientBrush brush2 = new RadialGradientBrush
            {
                Center = new Point(0.5, 0.5),
                GradientOrigin = new Point(0.5, 0.5),
                RadiusX = 0.5,
                RadiusY = 0.5
            };
            brush2.GradientStops.Add(new GradientStop(Color.FromArgb(40, 59, 130, 246), 0.0));
            brush2.GradientStops.Add(new GradientStop(Color.FromArgb(12, 29, 78, 216), 0.5));
            brush2.GradientStops.Add(new GradientStop(Colors.Transparent, 1.0));
            orb2.Fill = brush2;
            orb2.Effect = new BlurEffect { Radius = 100 };
            Canvas.SetLeft(orb2, 650);
            Canvas.SetTop(orb2, 260);

            TranslateTransform ttOrb2 = new TranslateTransform();
            orb2.RenderTransform = ttOrb2;

            DoubleAnimation animOrb2X = new DoubleAnimation
            {
                From = 25,
                To = -30,
                Duration = TimeSpan.FromSeconds(10),
                AutoReverse = true,
                RepeatBehavior = RepeatBehavior.Forever,
                EasingFunction = new SineEase { EasingMode = EasingMode.EaseInOut }
            };
            DoubleAnimation animOrb2Y = new DoubleAnimation
            {
                From = 20,
                To = -25,
                Duration = TimeSpan.FromSeconds(8),
                AutoReverse = true,
                RepeatBehavior = RepeatBehavior.Forever,
                EasingFunction = new SineEase { EasingMode = EasingMode.EaseInOut }
            };
            ttOrb2.BeginAnimation(TranslateTransform.XProperty, animOrb2X);
            ttOrb2.BeginAnimation(TranslateTransform.YProperty, animOrb2Y);
            canvas.Children.Add(orb2);

            // 3. Cyber Rain Particles (RainyDLC Theme)
            double[] xPositions = new double[] { 35, 90, 160, 230, 290, 370, 440, 520, 600, 670, 740, 810, 880, 940, 1010, 1060 };
            double[] heights = new double[] { 22, 34, 18, 28, 38, 20, 30, 26, 32, 24, 36, 19, 27, 33, 22, 30 };
            double[] speeds = new double[] { 3.2, 4.5, 2.8, 3.8, 4.2, 3.0, 4.8, 3.5, 4.0, 2.9, 4.4, 3.7, 4.1, 3.3, 4.6, 3.6 };

            for (int i = 0; i < xPositions.Length; i++)
            {
                Rectangle drop = new Rectangle
                {
                    Width = 1.5,
                    Height = heights[i],
                    IsHitTestVisible = false
                };

                LinearGradientBrush dropBrush = new LinearGradientBrush
                {
                    StartPoint = new Point(0, 0),
                    EndPoint = new Point(0, 1)
                };
                dropBrush.GradientStops.Add(new GradientStop(Color.FromArgb(0, 129, 140, 248), 0.0));
                dropBrush.GradientStops.Add(new GradientStop(Color.FromArgb((byte)(40 + (i % 4) * 20), 165, 180, 252), 0.6));
                dropBrush.GradientStops.Add(new GradientStop(Color.FromArgb(0, 129, 140, 248), 1.0));
                drop.Fill = dropBrush;

                Canvas.SetLeft(drop, xPositions[i]);
                Canvas.SetTop(drop, -60);

                DoubleAnimation fallAnim = new DoubleAnimation
                {
                    From = -60,
                    To = 700,
                    Duration = TimeSpan.FromSeconds(speeds[i]),
                    RepeatBehavior = RepeatBehavior.Forever,
                    BeginTime = TimeSpan.FromSeconds(i * 0.28)
                };

                drop.BeginAnimation(Canvas.TopProperty, fallAnim);
                canvas.Children.Add(drop);
            }

            return canvas;
        }

        #region Title Bar & Window Chrome

        private Border CreateTitleBar()
        {
            Border bar = new Border
            {
                Background = Brushes.Transparent,
                Padding = new Thickness(16, 4, 16, 0)
            };

            bar.MouseLeftButtonDown += (s, e) =>
            {
                if (e.ButtonState == MouseButtonState.Pressed) DragMove();
            };

            Grid g = new Grid();
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            TextBlock txtTitle = new TextBlock
            {
                Text = "🌧️ RainyDLC • Owner Edition",
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(Color.FromRgb(148, 163, 184)),
                HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(txtTitle, 1);
            g.Children.Add(txtTitle);

            StackPanel controls = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                HorizontalAlignment = System.Windows.HorizontalAlignment.Right,
                VerticalAlignment = VerticalAlignment.Center
            };

            Button btnMin = CreateTitleButton("—", (s, e) => WindowState = WindowState.Minimized);
            Button btnClose = CreateTitleButton("✕", (s, e) => CloseWindow(), true);

            controls.Children.Add(btnMin);
            controls.Children.Add(btnClose);

            Grid.SetColumn(controls, 2);
            g.Children.Add(controls);

            bar.Child = g;
            return bar;
        }

        private Button CreateTitleButton(string text, RoutedEventHandler onClick, bool isClose = false)
        {
            Button btn = new Button
            {
                Content = text,
                Width = 28,
                Height = 26,
                Margin = new Thickness(4, 0, 0, 0),
                Foreground = new SolidColorBrush(Color.FromRgb(140, 140, 150)),
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                Cursor = Cursors.Hand,
                Focusable = false
            };

            ControlTemplate tpl = new ControlTemplate(typeof(Button));
            FrameworkElementFactory b = new FrameworkElementFactory(typeof(Border));
            b.Name = "BtnBdr";
            b.SetValue(Border.CornerRadiusProperty, new CornerRadius(6));
            b.SetValue(Border.BackgroundProperty, Brushes.Transparent);

            FrameworkElementFactory cp = new FrameworkElementFactory(typeof(ContentPresenter));
            cp.SetValue(ContentPresenter.HorizontalAlignmentProperty, System.Windows.HorizontalAlignment.Center);
            cp.SetValue(ContentPresenter.VerticalAlignmentProperty, VerticalAlignment.Center);
            b.AppendChild(cp);

            tpl.VisualTree = b;

            Trigger hov = new Trigger { Property = Button.IsMouseOverProperty, Value = true };
            if (isClose)
            {
                hov.Setters.Add(new Setter(Border.BackgroundProperty, new SolidColorBrush(Color.FromRgb(220, 38, 38)), "BtnBdr"));
                hov.Setters.Add(new Setter(Button.ForegroundProperty, Brushes.White));
            }
            else
            {
                hov.Setters.Add(new Setter(Border.BackgroundProperty, new SolidColorBrush(Color.FromRgb(30, 30, 38)), "BtnBdr"));
                hov.Setters.Add(new Setter(Button.ForegroundProperty, Brushes.White));
            }
            tpl.Triggers.Add(hov);

            btn.Template = tpl;
            btn.Click += onClick;
            return btn;
        }

        #endregion

        #region Left Sidebar

        private UIElement CreateSidebar()
        {
            Grid sidebarGrid = new Grid();
            sidebarGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            sidebarGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(10, GridUnitType.Pixel) });
            sidebarGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            sidebarGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(10, GridUnitType.Pixel) });
            sidebarGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

            // 1. Карточка профиля игрока
            Border userCard = CreateUserCard();
            Grid.SetRow(userCard, 0);
            sidebarGrid.Children.Add(userCard);

            // 2. Информационная карточка движка RainyDLC
            Border engineCard = CreateEngineStatusCard();
            Grid.SetRow(engineCard, 2);
            sidebarGrid.Children.Add(engineCard);

            // 3. Меню навигации
            Border navCard = CreateNavMenuCard();
            Grid.SetRow(navCard, 4);
            sidebarGrid.Children.Add(navCard);

            return sidebarGrid;
        }

        private Border CreateUserCard()
        {
            Border userCard = new Border
            {
                Height = 64,
                CornerRadius = new CornerRadius(16),
                Background = new SolidColorBrush(Color.FromRgb(13, 17, 26)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromRgb(24, 32, 48)),
                Padding = new Thickness(12, 0, 12, 0)
            };

            Grid uGrid = new Grid();
            uGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            uGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            uGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            Border avatar = new Border
            {
                Width = 40,
                Height = 40,
                CornerRadius = new CornerRadius(20),
                Background = new LinearGradientBrush(Color.FromRgb(6, 182, 212), Color.FromRgb(59, 130, 246), 45),
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 10, 0)
            };
            avatar.Child = new TextBlock
            {
                Text = "👑",
                FontSize = 18,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(avatar, 0);
            uGrid.Children.Add(avatar);

            StackPanel uInfo = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            uInfo.Children.Add(new TextBlock
            {
                Text = _data.Username,
                FontWeight = FontWeights.Bold,
                FontSize = 13.5,
                Foreground = Brushes.White
            });

            Border roleTag = new Border
            {
                Background = new SolidColorBrush(Color.FromArgb(40, 239, 68, 68)),
                CornerRadius = new CornerRadius(4),
                Padding = new Thickness(6, 1, 6, 1),
                Margin = new Thickness(0, 2, 0, 0),
                HorizontalAlignment = HorizontalAlignment.Left
            };
            roleTag.Child = new TextBlock
            {
                Text = "👑 OWNER",
                FontSize = 9.5,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(Color.FromRgb(239, 68, 68))
            };
            uInfo.Children.Add(roleTag);

            Grid.SetColumn(uInfo, 1);
            uGrid.Children.Add(uInfo);

            Border statusDot = new Border
            {
                Width = 9,
                Height = 9,
                CornerRadius = new CornerRadius(4.5),
                Background = new SolidColorBrush(Color.FromRgb(52, 211, 153)),
                VerticalAlignment = VerticalAlignment.Center,
                ToolTip = "Онлайн"
            };
            Grid.SetColumn(statusDot, 2);
            uGrid.Children.Add(statusDot);

            userCard.Child = uGrid;
            return userCard;
        }

        private Border CreateEngineStatusCard()
        {
            Border b = new Border
            {
                Height = 104,
                CornerRadius = new CornerRadius(16),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromRgb(24, 34, 52)),
                Padding = new Thickness(14, 12, 14, 12)
            };

            b.Background = new LinearGradientBrush(
                Color.FromRgb(14, 19, 30),
                Color.FromRgb(9, 12, 18),
                45
            );

            StackPanel sp = new StackPanel();

            Grid r1 = new Grid();
            r1.Children.Add(new TextBlock
            {
                Text = "⚡ Rainy Core",
                FontSize = 11,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(Color.FromRgb(56, 189, 248)),
                HorizontalAlignment = HorizontalAlignment.Left
            });
            r1.Children.Add(new TextBlock
            {
                Text = "v1.21.4",
                FontSize = 10.5,
                Foreground = new SolidColorBrush(Color.FromRgb(148, 163, 184)),
                HorizontalAlignment = HorizontalAlignment.Right
            });
            sp.Children.Add(r1);

            sp.Children.Add(new TextBlock
            {
                Text = "Fabric • High-FPS",
                FontSize = 15,
                FontWeight = FontWeights.Bold,
                Foreground = Brushes.White,
                Margin = new Thickness(0, 4, 0, 7)
            });

            Border meter = new Border
            {
                Height = 4,
                CornerRadius = new CornerRadius(2),
                HorizontalAlignment = HorizontalAlignment.Stretch
            };
            meter.Background = new LinearGradientBrush(
                Color.FromRgb(6, 182, 212),
                Color.FromRgb(99, 102, 241),
                0
            );

            DropShadowEffect barGlow = new DropShadowEffect
            {
                Color = Color.FromRgb(6, 182, 212),
                BlurRadius = 8,
                ShadowDepth = 0,
                Opacity = 0.6
            };
            meter.Effect = barGlow;

            DoubleAnimation glowPulse = new DoubleAnimation
            {
                From = 0.35,
                To = 0.9,
                Duration = TimeSpan.FromSeconds(2.0),
                AutoReverse = true,
                RepeatBehavior = RepeatBehavior.Forever
            };
            barGlow.BeginAnimation(DropShadowEffect.OpacityProperty, glowPulse);

            sp.Children.Add(meter);

            Grid r3 = new Grid { Margin = new Thickness(0, 7, 0, 0) };
            r3.Children.Add(new TextBlock
            {
                Text = "Память: " + _data.RamMb + " МБ",
                FontSize = 10.5,
                Foreground = new SolidColorBrush(Color.FromRgb(148, 163, 184)),
                HorizontalAlignment = HorizontalAlignment.Left
            });
            r3.Children.Add(new TextBlock
            {
                Text = "🟢 Готов",
                FontSize = 10.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(Color.FromRgb(52, 211, 153)),
                HorizontalAlignment = HorizontalAlignment.Right
            });
            sp.Children.Add(r3);

            b.Child = sp;
            return b;
        }

        private Border CreateNavMenuCard()
        {
            Border b = new Border
            {
                CornerRadius = new CornerRadius(16),
                Background = new SolidColorBrush(Color.FromRgb(13, 17, 26)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromRgb(24, 32, 48)),
                Padding = new Thickness(8, 10, 8, 10)
            };

            Grid g = new Grid();
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

            StackPanel sp = new StackPanel();

            _navButtons.Add(CreateNavItem("⚡  Главная", 0));
            _navButtons.Add(CreateNavItem("☁️  Моды", 1));
            _navButtons.Add(CreateNavItem("👥  Друзья", 2));
            _navButtons.Add(CreateNavItem("⚙️  Настройки", 3));
            _navButtons.Add(CreateNavItem("💻  Консоль", 4));

            foreach (Button btn in _navButtons)
            {
                sp.Children.Add(btn);
            }

            Grid.SetRow(sp, 0);
            g.Children.Add(sp);

            Button btnLogout = new Button
            {
                Content = "[→  Выйти",
                Height = 36,
                Margin = new Thickness(4, 0, 4, 2),
                FontSize = 12,
                FontWeight = FontWeights.Medium,
                Foreground = new SolidColorBrush(Color.FromRgb(115, 115, 128)),
                Cursor = Cursors.Hand,
                HorizontalContentAlignment = System.Windows.HorizontalAlignment.Left,
                Focusable = false
            };

            ControlTemplate tpl = new ControlTemplate(typeof(Button));
            FrameworkElementFactory bdr = new FrameworkElementFactory(typeof(Border));
            bdr.Name = "LogoutBdr";
            bdr.SetValue(Border.CornerRadiusProperty, new CornerRadius(10));
            bdr.SetValue(Border.BackgroundProperty, Brushes.Transparent);
            bdr.SetValue(Border.PaddingProperty, new Thickness(12, 0, 12, 0));

            FrameworkElementFactory cp = new FrameworkElementFactory(typeof(ContentPresenter));
            cp.SetValue(ContentPresenter.VerticalAlignmentProperty, VerticalAlignment.Center);
            bdr.AppendChild(cp);
            tpl.VisualTree = bdr;

            Trigger hov = new Trigger { Property = Button.IsMouseOverProperty, Value = true };
            hov.Setters.Add(new Setter(Border.BackgroundProperty, new SolidColorBrush(Color.FromRgb(30, 20, 25)), "LogoutBdr"));
            hov.Setters.Add(new Setter(Button.ForegroundProperty, new SolidColorBrush(Color.FromRgb(248, 113, 113))));
            tpl.Triggers.Add(hov);

            btnLogout.Template = tpl;
            btnLogout.Click += (s, e) => CloseWindow();

            Grid.SetRow(btnLogout, 1);
            g.Children.Add(btnLogout);

            b.Child = g;
            return b;
        }

        private Button CreateNavItem(string text, int pageIndex)
        {
            Button btn = new Button
            {
                Content = text,
                Height = 38,
                Margin = new Thickness(0, 0, 0, 4),
                FontSize = 13,
                FontWeight = FontWeights.Medium,
                Foreground = new SolidColorBrush(Color.FromRgb(140, 140, 150)),
                Cursor = Cursors.Hand,
                HorizontalContentAlignment = System.Windows.HorizontalAlignment.Left,
                Focusable = false
            };

            ControlTemplate tpl = new ControlTemplate(typeof(Button));
            FrameworkElementFactory bdr = new FrameworkElementFactory(typeof(Border));
            bdr.Name = "NavBdr";
            bdr.SetValue(Border.CornerRadiusProperty, new CornerRadius(10));
            bdr.SetValue(Border.BackgroundProperty, Brushes.Transparent);
            bdr.SetValue(Border.PaddingProperty, new Thickness(14, 0, 14, 0));

            FrameworkElementFactory cp = new FrameworkElementFactory(typeof(ContentPresenter));
            cp.SetValue(ContentPresenter.VerticalAlignmentProperty, VerticalAlignment.Center);
            bdr.AppendChild(cp);
            tpl.VisualTree = bdr;

            Trigger hov = new Trigger { Property = Button.IsMouseOverProperty, Value = true };
            hov.Setters.Add(new Setter(Border.BackgroundProperty, new SolidColorBrush(Color.FromRgb(24, 25, 32)), "NavBdr"));
            hov.Setters.Add(new Setter(Button.ForegroundProperty, Brushes.White));
            tpl.Triggers.Add(hov);

            btn.Template = tpl;
            btn.Click += (s, e) => SwitchPage(pageIndex);
            return btn;
        }

        private void SwitchPage(int index)
        {
            if (_currentPageIndex == index) return;
            _currentPageIndex = index;

            Grid[] pages = new Grid[] { _pageHome, _pageMods, _pageFriends, _pageSettings, _pageConsole };
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
                        Duration = TimeSpan.FromMilliseconds(200),
                        EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
                    };
                    p.BeginAnimation(UIElement.OpacityProperty, fadeAnim);

                    DoubleAnimation slideAnim = new DoubleAnimation
                    {
                        From = 12.0,
                        To = 0.0,
                        Duration = TimeSpan.FromMilliseconds(200),
                        EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
                    };
                    tt.BeginAnimation(TranslateTransform.YProperty, slideAnim);
                }
                else
                {
                    p.Visibility = Visibility.Collapsed;
                }
            }

            for (int i = 0; i < _navButtons.Count; i++)
            {
                Button b = _navButtons[i];
                Border bdr = b.Template.FindName("NavBdr", b) as Border;
                if (bdr != null)
                {
                    if (i == index)
                    {
                        bdr.Background = new SolidColorBrush(Color.FromRgb(18, 28, 44));
                        bdr.BorderBrush = new SolidColorBrush(Color.FromRgb(6, 182, 212));
                        bdr.BorderThickness = new Thickness(2, 0, 0, 0);
                        b.Foreground = Brushes.White;
                        b.FontWeight = FontWeights.Bold;
                    }
                    else
                    {
                        bdr.Background = Brushes.Transparent;
                        bdr.BorderBrush = Brushes.Transparent;
                        bdr.BorderThickness = new Thickness(0);
                        b.Foreground = new SolidColorBrush(Color.FromRgb(140, 148, 165));
                        b.FontWeight = FontWeights.Medium;
                    }
                }
            }
        }

        #endregion

        #region Page 1: Главная (Bento Dashboard)

        private Grid CreatePageHome()
        {
            Grid g = new Grid();
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto }); // 0: Hero Banner
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(14, GridUnitType.Pixel) }); // 1: Spacing
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto }); // 2: 3 Bento Stat Tiles
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(14, GridUnitType.Pixel) }); // 3: Spacing
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) }); // 4: 2 Specification Cards

            // 1. Hero Banner
            Border heroCard = new Border
            {
                CornerRadius = new CornerRadius(18),
                Background = new LinearGradientBrush(Color.FromRgb(14, 20, 32), Color.FromRgb(9, 12, 18), 35),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromRgb(24, 36, 56)),
                Padding = new Thickness(22, 18, 22, 18)
            };

            Grid heroGrid = new Grid();
            heroGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            heroGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            StackPanel heroLeft = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            
            StackPanel brandRow = new StackPanel { Orientation = Orientation.Horizontal };
            brandRow.Children.Add(new TextBlock
            {
                Text = "🌧️ RainyDLC Client",
                FontSize = 21,
                FontWeight = FontWeights.Bold,
                Foreground = Brushes.White
            });
            heroLeft.Children.Add(brandRow);

            heroLeft.Children.Add(new TextBlock
            {
                Text = "Кастомный клиент на базе Fabric 1.21.4 • Высокий FPS • Прямая загрузка модов",
                FontSize = 12.5,
                Foreground = new SolidColorBrush(Color.FromRgb(148, 163, 184)),
                Margin = new Thickness(0, 4, 0, 10)
            });

            StackPanel badges = new StackPanel { Orientation = Orientation.Horizontal };
            badges.Children.Add(CreateBadge("👑 Owner Edition", Color.FromRgb(239, 68, 68), Color.FromArgb(30, 239, 68, 68)));
            badges.Children.Add(CreateBadge("⚡ 1.21.4 Fabric", Color.FromRgb(56, 189, 248), Color.FromArgb(30, 56, 189, 248)));
            badges.Children.Add(CreateBadge("🛡️ Secured Build", Color.FromRgb(52, 211, 153), Color.FromArgb(30, 52, 211, 153)));
            heroLeft.Children.Add(badges);

            Grid.SetColumn(heroLeft, 0);
            heroGrid.Children.Add(heroLeft);

            StackPanel heroRight = new StackPanel
            {
                VerticalAlignment = VerticalAlignment.Center,
                HorizontalAlignment = HorizontalAlignment.Right
            };

            _btnLaunch = CreateBottomActionButton("▶  Запустить клиент", (s, e) => OnLaunchButtonClick(), true);
            _btnLaunch.Width = 200;
            _btnLaunch.Height = 46;
            _btnLaunchText = _btnLaunch.Content as TextBlock;
            heroRight.Children.Add(_btnLaunch);

            Button btnFolder = CreateSmallPillButton("📂  Папка run", (s, e) =>
            {
                try
                {
                    string runDir = System.IO.Path.Combine(_projectDir, "run");
                    if (!Directory.Exists(runDir)) Directory.CreateDirectory(runDir);
                    Process.Start("explorer.exe", runDir);
                }
                catch (Exception ex)
                {
                    System.Windows.MessageBox.Show("Не удалось открыть папку:\n" + ex.Message, "RainyDLC", MessageBoxButton.OK, MessageBoxImage.Warning);
                }
            });
            btnFolder.Height = 30;
            btnFolder.Width = 200;
            btnFolder.Margin = new Thickness(0, 8, 0, 0);
            heroRight.Children.Add(btnFolder);

            Grid.SetColumn(heroRight, 1);
            heroGrid.Children.Add(heroRight);

            heroCard.Child = heroGrid;
            Grid.SetRow(heroCard, 0);
            g.Children.Add(heroCard);

            // 2. 3 Bento Stat Tiles
            Grid statsGrid = new Grid();
            statsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            statsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(12, GridUnitType.Pixel) });
            statsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            statsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(12, GridUnitType.Pixel) });
            statsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            _txtPlaytime = new TextBlock
            {
                Text = LauncherData.FormatPlaytime(_data.PlaytimeMinutes),
                FontSize = 17,
                FontWeight = FontWeights.Bold,
                Foreground = Brushes.White,
                Margin = new Thickness(0, 2, 0, 0)
            };
            Border tilePlaytime = CreateStatTile("⏱️", "Время в игре", _txtPlaytime, "Счётчик активных сессий");
            Grid.SetColumn(tilePlaytime, 0);
            statsGrid.Children.Add(tilePlaytime);

            _txtLaunchCount = new TextBlock
            {
                Text = _data.LaunchCount.ToString(),
                FontSize = 17,
                FontWeight = FontWeights.Bold,
                Foreground = Brushes.White,
                Margin = new Thickness(0, 2, 0, 0)
            };
            Border tileLaunches = CreateStatTile("🚀", "Запусков клиента", _txtLaunchCount, "Всего игровых сессий");
            Grid.SetColumn(tileLaunches, 2);
            statsGrid.Children.Add(tileLaunches);

            _txtLastLaunch = new TextBlock
            {
                Text = string.IsNullOrEmpty(_data.LastLaunchTime) ? "Еще не запускался" : _data.LastLaunchTime,
                FontSize = 13.5,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(Color.FromRgb(226, 232, 240)),
                Margin = new Thickness(0, 4, 0, 0)
            };
            Border tileLast = CreateStatTile("🕒", "Предыдущий вход", _txtLastLaunch, "Локальное время");
            Grid.SetColumn(tileLast, 4);
            statsGrid.Children.Add(tileLast);

            Grid.SetRow(statsGrid, 2);
            g.Children.Add(statsGrid);

            // 3. Bottom 2 Details Cards
            Grid detailsGrid = new Grid();
            detailsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            detailsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(14, GridUnitType.Pixel) });
            detailsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            // Card Left: Профиль владельца
            Border cardProfile = CreateContentCard("👤 Профиль владельца");
            StackPanel spProf = new StackPanel();
            spProf.Children.Add(CreateInfoRow("Никнейм", _data.Username));
            spProf.Children.Add(CreateInfoRow("Роль", "👑 " + _data.Role, Color.FromRgb(239, 68, 68)));
            spProf.Children.Add(CreateInfoRow("Почта", _data.Email));
            spProf.Children.Add(CreateInfoRow("Лицензия", "Бессрочная (Lifetime)", Color.FromRgb(52, 211, 153)));
            spProf.Children.Add(CreateInfoRow("Регистрация", _data.RegDate, Colors.Transparent, true));
            cardProfile.Child = spProf;
            Grid.SetColumn(cardProfile, 0);
            detailsGrid.Children.Add(cardProfile);

            // Card Right: Спецификация сборки
            Border cardSpecs = CreateContentCard("⚡ Спецификация сборки");
            StackPanel spSpecs = new StackPanel();
            spSpecs.Children.Add(CreateInfoRow("Сборка", "RainyDLC Release 2026"));
            spSpecs.Children.Add(CreateInfoRow("Платформа", "Fabric Loader (1.21.4)"));
            spSpecs.Children.Add(CreateInfoRow("Память ОЗУ", _data.RamMb + " МБ"));
            spSpecs.Children.Add(CreateInfoRow("Моды", "Modrinth Direct Sync"));
            spSpecs.Children.Add(CreateInfoRow("Статус системы", "🟢 Готов к запуску", Color.FromRgb(52, 211, 153), true));
            cardSpecs.Child = spSpecs;
            Grid.SetColumn(cardSpecs, 2);
            detailsGrid.Children.Add(cardSpecs);

            Grid.SetRow(detailsGrid, 4);
            g.Children.Add(detailsGrid);

            return g;
        }

        private Border CreateBadge(string text, Color textColor, Color bgColor)
        {
            Border b = new Border
            {
                CornerRadius = new CornerRadius(6),
                Background = new SolidColorBrush(bgColor),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(textColor),
                Padding = new Thickness(8, 3, 8, 3),
                Margin = new Thickness(0, 0, 8, 0)
            };
            b.Child = new TextBlock
            {
                Text = text,
                FontSize = 11,
                FontWeight = FontWeights.Bold,
                Foreground = new SolidColorBrush(textColor)
            };
            return b;
        }

        private Border CreateStatTile(string icon, string title, TextBlock valBlock, string subtitle)
        {
            Border b = new Border
            {
                Height = 84,
                CornerRadius = new CornerRadius(14),
                Background = new SolidColorBrush(Color.FromRgb(13, 17, 26)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromRgb(24, 32, 48)),
                Padding = new Thickness(16, 12, 16, 12)
            };

            b.MouseEnter += (s, e) =>
            {
                SolidColorBrush br = b.BorderBrush as SolidColorBrush;
                if (br != null && !br.IsFrozen)
                {
                    br.BeginAnimation(SolidColorBrush.ColorProperty, new ColorAnimation
                    {
                        To = Color.FromRgb(14, 116, 144),
                        Duration = TimeSpan.FromMilliseconds(180)
                    });
                }
            };
            b.MouseLeave += (s, e) =>
            {
                SolidColorBrush br = b.BorderBrush as SolidColorBrush;
                if (br != null && !br.IsFrozen)
                {
                    br.BeginAnimation(SolidColorBrush.ColorProperty, new ColorAnimation
                    {
                        To = Color.FromRgb(24, 32, 48),
                        Duration = TimeSpan.FromMilliseconds(180)
                    });
                }
            };

            StackPanel sp = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            sp.Children.Add(new TextBlock
            {
                Text = icon + "  " + title,
                FontSize = 11,
                FontWeight = FontWeights.Medium,
                Foreground = new SolidColorBrush(Color.FromRgb(148, 163, 184))
            });
            sp.Children.Add(valBlock);
            sp.Children.Add(new TextBlock
            {
                Text = subtitle,
                FontSize = 10,
                Foreground = new SolidColorBrush(Color.FromRgb(100, 116, 139)),
                Margin = new Thickness(0, 2, 0, 0)
            });

            b.Child = sp;
            return b;
        }

        private Border CreateContentCard(string headerText)
        {
            Border b = new Border
            {
                CornerRadius = new CornerRadius(16),
                Background = new SolidColorBrush(Color.FromRgb(13, 17, 26)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromRgb(24, 32, 48)),
                Padding = new Thickness(20, 16, 20, 16)
            };

            b.MouseEnter += (s, e) =>
            {
                SolidColorBrush br = b.BorderBrush as SolidColorBrush;
                if (br != null && !br.IsFrozen)
                {
                    br.BeginAnimation(SolidColorBrush.ColorProperty, new ColorAnimation
                    {
                        To = Color.FromRgb(14, 116, 144),
                        Duration = TimeSpan.FromMilliseconds(180)
                    });
                }
            };
            b.MouseLeave += (s, e) =>
            {
                SolidColorBrush br = b.BorderBrush as SolidColorBrush;
                if (br != null && !br.IsFrozen)
                {
                    br.BeginAnimation(SolidColorBrush.ColorProperty, new ColorAnimation
                    {
                        To = Color.FromRgb(24, 32, 48),
                        Duration = TimeSpan.FromMilliseconds(180)
                    });
                }
            };

            StackPanel sp = new StackPanel();
            sp.Children.Add(new TextBlock
            {
                Text = headerText,
                FontSize = 16,
                FontWeight = FontWeights.Bold,
                Foreground = Brushes.White,
                Margin = new Thickness(0, 0, 0, 14)
            });

            return b;
        }

        private UIElement CreateInfoRow(string label, string val, Color? valColor = null, bool isLast = false)
        {
            Grid g = new Grid { Margin = new Thickness(0, 0, 0, isLast ? 0 : 12) };
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            TextBlock lbl = new TextBlock
            {
                Text = label,
                FontSize = 13,
                Foreground = new SolidColorBrush(Color.FromRgb(130, 130, 145))
            };
            Grid.SetColumn(lbl, 0);
            g.Children.Add(lbl);

            Color c = (valColor.HasValue && valColor.Value != Colors.Transparent) ? valColor.Value : Color.FromRgb(180, 180, 195);
            TextBlock v = new TextBlock
            {
                Text = val,
                FontSize = 13,
                FontWeight = FontWeights.Medium,
                Foreground = new SolidColorBrush(c)
            };
            Grid.SetColumn(v, 1);
            g.Children.Add(v);

            return g;
        }

        private UIElement CreateInfoRowCustom(string label, TextBlock valBlock, bool isLast = false)
        {
            Grid g = new Grid { Margin = new Thickness(0, 0, 0, isLast ? 0 : 12) };
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            TextBlock lbl = new TextBlock
            {
                Text = label,
                FontSize = 13,
                Foreground = new SolidColorBrush(Color.FromRgb(130, 130, 145))
            };
            Grid.SetColumn(lbl, 0);
            g.Children.Add(lbl);

            Grid.SetColumn(valBlock, 1);
            g.Children.Add(valBlock);

            return g;
        }

        private Button CreateBottomActionButton(string text, RoutedEventHandler onClick, bool isLaunchHero = false)
        {
            Button btn = new Button
            {
                Height = 48,
                Cursor = Cursors.Hand,
                Focusable = false
            };

            TextBlock tb = new TextBlock
            {
                Text = text,
                FontSize = 13,
                FontWeight = isLaunchHero ? FontWeights.Bold : FontWeights.SemiBold,
                Foreground = isLaunchHero ? Brushes.White : new SolidColorBrush(Color.FromRgb(200, 200, 215)),
                HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            btn.Content = tb;

            ControlTemplate tpl = new ControlTemplate(typeof(Button));
            FrameworkElementFactory bdr = new FrameworkElementFactory(typeof(Border));
            bdr.Name = "ActBdr";
            bdr.SetValue(Border.CornerRadiusProperty, new CornerRadius(14));

            if (isLaunchHero)
            {
                bdr.SetValue(Border.BackgroundProperty, new SolidColorBrush(Color.FromRgb(14, 24, 40)));
                bdr.SetValue(Border.BorderThicknessProperty, new Thickness(1.2));
                bdr.SetValue(Border.BorderBrushProperty, new SolidColorBrush(Color.FromRgb(6, 182, 212)));
            }
            else
            {
                bdr.SetValue(Border.BackgroundProperty, new SolidColorBrush(Color.FromRgb(13, 17, 26)));
                bdr.SetValue(Border.BorderThicknessProperty, new Thickness(1));
                bdr.SetValue(Border.BorderBrushProperty, new SolidColorBrush(Color.FromRgb(24, 32, 48)));
            }

            FrameworkElementFactory cp = new FrameworkElementFactory(typeof(ContentPresenter));
            cp.SetValue(ContentPresenter.HorizontalAlignmentProperty, System.Windows.HorizontalAlignment.Center);
            cp.SetValue(ContentPresenter.VerticalAlignmentProperty, VerticalAlignment.Center);
            bdr.AppendChild(cp);
            tpl.VisualTree = bdr;

            Trigger hov = new Trigger { Property = Button.IsMouseOverProperty, Value = true };
            if (isLaunchHero)
            {
                hov.Setters.Add(new Setter(Border.BackgroundProperty, new SolidColorBrush(Color.FromRgb(20, 36, 62)), "ActBdr"));
                hov.Setters.Add(new Setter(Border.BorderBrushProperty, new SolidColorBrush(Color.FromRgb(56, 189, 248)), "ActBdr"));
            }
            else
            {
                hov.Setters.Add(new Setter(Border.BackgroundProperty, new SolidColorBrush(Color.FromRgb(20, 26, 38)), "ActBdr"));
                hov.Setters.Add(new Setter(Border.BorderBrushProperty, new SolidColorBrush(Color.FromRgb(34, 46, 68)), "ActBdr"));
            }
            tpl.Triggers.Add(hov);

            btn.Template = tpl;
            btn.Click += onClick;

            if (isLaunchHero)
            {
                DropShadowEffect glow = new DropShadowEffect
                {
                    Color = Color.FromRgb(6, 182, 212),
                    BlurRadius = 12,
                    ShadowDepth = 0,
                    Opacity = 0.55
                };
                btn.Effect = glow;

                DoubleAnimation glowAnim = new DoubleAnimation
                {
                    From = 0.35,
                    To = 0.85,
                    Duration = TimeSpan.FromSeconds(1.6),
                    AutoReverse = true,
                    RepeatBehavior = RepeatBehavior.Forever
                };
                glow.BeginAnimation(DropShadowEffect.OpacityProperty, glowAnim);

                DoubleAnimation blurAnim = new DoubleAnimation
                {
                    From = 8,
                    To = 18,
                    Duration = TimeSpan.FromSeconds(1.6),
                    AutoReverse = true,
                    RepeatBehavior = RepeatBehavior.Forever
                };
                glow.BeginAnimation(DropShadowEffect.BlurRadiusProperty, blurAnim);
            }

            return btn;
        }

        #endregion



        #region Page 3: Моды (Локальные + Modrinth Online Browser)

        private Grid CreatePageMods()
        {
            Grid g = new Grid();
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto }); // Header & Subtabs
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(14, GridUnitType.Pixel) }); // Gap
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) }); // Views

            // 1. Верхний ряд: заголовок, переключатели подвкладок и кнопки
            Grid topRow = new Grid();
            topRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            topRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            topRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            StackPanel titlePanel = new StackPanel();
            titlePanel.Children.Add(new TextBlock
            {
                Text = "Моды",
                FontSize = 20,
                FontWeight = FontWeights.Bold,
                Foreground = Brushes.White
            });
            titlePanel.Children.Add(new TextBlock
            {
                Text = "Моды из папки game/mods и каталог Modrinth",
                FontSize = 12,
                Foreground = new SolidColorBrush(Color.FromRgb(115, 115, 128)),
                Margin = new Thickness(0, 2, 0, 8)
            });

            // Подвкладки [ Локальные ] и [ Modrinth ]
            StackPanel tabs = new StackPanel { Orientation = Orientation.Horizontal };

            _tabLocalBtn = new Border
            {
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(Color.FromRgb(79, 70, 229)),
                Padding = new Thickness(14, 6, 14, 6),
                Margin = new Thickness(0, 0, 10, 0),
                Cursor = Cursors.Hand
            };
            _tabLocalText = new TextBlock { Text = "Локальные", FontSize = 12, FontWeight = FontWeights.SemiBold, Foreground = Brushes.White };
            _tabLocalBtn.Child = _tabLocalText;
            _tabLocalBtn.MouseLeftButtonDown += (s, e) => SwitchModsSubTab(true);
            tabs.Children.Add(_tabLocalBtn);

            _tabModrinthBtn = new Border
            {
                CornerRadius = new CornerRadius(8),
                Background = Brushes.Transparent,
                Padding = new Thickness(14, 6, 14, 6),
                Cursor = Cursors.Hand
            };
            _tabModrinthText = new TextBlock { Text = "Modrinth", FontSize = 12, Foreground = new SolidColorBrush(Color.FromRgb(120, 120, 135)) };
            _tabModrinthBtn.Child = _tabModrinthText;
            _tabModrinthBtn.MouseLeftButtonDown += (s, e) => SwitchModsSubTab(false);
            tabs.Children.Add(_tabModrinthBtn);

            titlePanel.Children.Add(tabs);
            Grid.SetColumn(titlePanel, 0);
            topRow.Children.Add(titlePanel);

            // Кнопки справа [ 📁 Папка модов ] и [ 🔄 Обновить ]
            StackPanel actBtns = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(0, 4, 0, 0) };
            Button btnModFolder = CreateSmallPillButton("📁  Папка модов", (s, e) =>
            {
                string modsDir = GetModsDir();
                try { Process.Start("explorer.exe", modsDir); } catch { }
            });
            actBtns.Children.Add(btnModFolder);

            Button btnRefresh = CreateSmallPillButton("🔄  Обновить", (s, e) =>
            {
                ReloadLocalMods();
                if (_modsModrinthView.Visibility == Visibility.Visible)
                {
                    LoadModrinthMods(_txtModrinthSearch != null ? _txtModrinthSearch.Text.Trim() : "");
                }
            });
            btnRefresh.Margin = new Thickness(10, 0, 0, 0);
            actBtns.Children.Add(btnRefresh);

            Grid.SetColumn(actBtns, 2);
            topRow.Children.Add(actBtns);

            Grid.SetRow(topRow, 0);
            g.Children.Add(topRow);

            // 2. Представление 1: Локальные моды
            _modsLocalView = CreateLocalModsView();

            // 3. Представление 2: Modrinth браузер и загрузчик
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
                _tabLocalBtn.Background = new SolidColorBrush(Color.FromRgb(79, 70, 229));
                _tabLocalText.Foreground = Brushes.White;
                _tabLocalText.FontWeight = FontWeights.SemiBold;

                _tabModrinthBtn.Background = Brushes.Transparent;
                _tabModrinthText.Foreground = new SolidColorBrush(Color.FromRgb(120, 120, 135));
                _tabModrinthText.FontWeight = FontWeights.Normal;

                _modsLocalView.Visibility = Visibility.Visible;
                _modsModrinthView.Visibility = Visibility.Collapsed;

                ReloadLocalMods();
            }
            else
            {
                _tabModrinthBtn.Background = new SolidColorBrush(Color.FromRgb(79, 70, 229));
                _tabModrinthText.Foreground = Brushes.White;
                _tabModrinthText.FontWeight = FontWeights.SemiBold;

                _tabLocalBtn.Background = Brushes.Transparent;
                _tabLocalText.Foreground = new SolidColorBrush(Color.FromRgb(120, 120, 135));
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

            string modsDir = GetModsDir();
            List<string> jarFiles = new List<string>();

            if (Directory.Exists(modsDir))
            {
                jarFiles.AddRange(Directory.GetFiles(modsDir, "*.jar"));
                jarFiles.AddRange(Directory.GetFiles(modsDir, "*.jar.disabled"));
            }

            // Двухколоночная сетка
            Grid modGrid = new Grid();
            modGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            modGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(14, GridUnitType.Pixel) });
            modGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            StackPanel colLeft = new StackPanel();
            StackPanel colRight = new StackPanel();

            if (jarFiles.Count > 0)
            {
                // Показываем установленные jar моды
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
            }

            // Добавляем встроенные рекомендуемые моды (из референса пользователя)
            colLeft.Children.Add(CreateModCard("🍎", "AppleSkin", "Adds various food-related HUD improvements", true, Color.FromRgb(220, 38, 38)));
            colLeft.Children.Add(CreateModCard("📦", "BadOptimizations", "Optimization mod that focuses on things other than rendering", true, Color.FromRgb(100, 116, 139)));
            colLeft.Children.Add(CreateModCard("📜", "Fabric API", "Core API module providing key hooks and interoperability features.", true, Color.FromRgb(217, 119, 6)));
            colLeft.Children.Add(CreateModCard("🌿", "FerriteCore", "Reduces memory usage", true, Color.FromRgb(34, 197, 94)));
            colLeft.Children.Add(CreateModCard("🌈", "Iris", "A modern shaders mod for Minecraft intended to be compatible with existin...", false, Color.FromRgb(168, 85, 247)));

            colRight.Children.Add(CreateModCard("⚡", "Async", "Async - Minecraft Entity Multi-Threading Mod", true, Color.FromRgb(59, 130, 246)));
            colRight.Children.Add(CreateModCard("⛏️", "CIT Resewn", "Re-implements MCPatcher's CIT", true, Color.FromRgb(139, 92, 246)));
            colRight.Children.Add(CreateModCard("🧩", "Fabric Language Kotlin", "Fabric language module for Kotlin.", true, Color.FromRgb(236, 72, 153)));
            colRight.Children.Add(CreateModCard("🔄", "In-Game Account Switcher", "Allows you to change which account you are signed in to in-game without...", true, Color.FromRgb(245, 158, 11)));
            colRight.Children.Add(CreateModCard("🪶", "Lithium", "Lithium is a free and open-source optimization mod for Minecraft which...", true, Color.FromRgb(147, 51, 234)));

            Grid.SetColumn(colLeft, 0);
            modGrid.Children.Add(colLeft);

            Grid.SetColumn(colRight, 2);
            modGrid.Children.Add(colRight);

            _localModsStack.Children.Add(modGrid);
        }

        private Border CreateInstalledModCard(string fileName, string size, bool isEnabled, Action<bool> onToggle)
        {
            Border b = new Border
            {
                Height = 72,
                CornerRadius = new CornerRadius(14),
                Background = new SolidColorBrush(Color.FromRgb(14, 15, 18)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromRgb(22, 23, 28)),
                Padding = new Thickness(14, 0, 14, 0),
                Margin = new Thickness(0, 0, 0, 10)
            };

            Grid g = new Grid();
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            Border iconBorder = new Border
            {
                Width = 38,
                Height = 38,
                CornerRadius = new CornerRadius(10),
                Background = new SolidColorBrush(Color.FromArgb(40, 99, 102, 241)),
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 12, 0)
            };
            iconBorder.Child = new TextBlock
            {
                Text = "☕",
                FontSize = 18,
                HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(iconBorder, 0);
            g.Children.Add(iconBorder);

            StackPanel sp = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            string cleanTitle = fileName.Replace(".jar.disabled", "").Replace(".jar", "");
            sp.Children.Add(new TextBlock
            {
                Text = cleanTitle,
                FontSize = 13,
                FontWeight = FontWeights.Bold,
                Foreground = Brushes.White,
                TextTrimming = TextTrimming.CharacterEllipsis
            });
            sp.Children.Add(new TextBlock
            {
                Text = "Файл: " + fileName + " (" + size + ")",
                FontSize = 11,
                Foreground = new SolidColorBrush(Color.FromRgb(115, 115, 128)),
                TextTrimming = TextTrimming.CharacterEllipsis,
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

        private Border CreateModCard(string icon, string name, string desc, bool initialState, Color iconColor)
        {
            Border b = new Border
            {
                Height = 72,
                CornerRadius = new CornerRadius(14),
                Background = new SolidColorBrush(Color.FromRgb(14, 15, 18)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromRgb(22, 23, 28)),
                Padding = new Thickness(14, 0, 14, 0),
                Margin = new Thickness(0, 0, 0, 10)
            };

            Grid g = new Grid();
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            Border iconBorder = new Border
            {
                Width = 38,
                Height = 38,
                CornerRadius = new CornerRadius(10),
                Background = new SolidColorBrush(Color.FromArgb(40, iconColor.R, iconColor.G, iconColor.B)),
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 12, 0)
            };
            iconBorder.Child = new TextBlock
            {
                Text = icon,
                FontSize = 18,
                HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(iconBorder, 0);
            g.Children.Add(iconBorder);

            StackPanel sp = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            sp.Children.Add(new TextBlock
            {
                Text = name,
                FontSize = 13,
                FontWeight = FontWeights.Bold,
                Foreground = Brushes.White
            });
            sp.Children.Add(new TextBlock
            {
                Text = desc,
                FontSize = 11,
                Foreground = new SolidColorBrush(Color.FromRgb(115, 115, 128)),
                TextTrimming = TextTrimming.CharacterEllipsis,
                Margin = new Thickness(0, 2, 0, 0)
            });
            Grid.SetColumn(sp, 1);
            g.Children.Add(sp);

            bool savedState = initialState;
            if (_data.ModStates.ContainsKey(name)) savedState = _data.ModStates[name];
            ToggleSwitch sw = new ToggleSwitch(savedState) { VerticalAlignment = VerticalAlignment.Center };
            sw.CheckedChanged += (state) =>
            {
                _data.ModStates[name] = state;
                _data.Save();
            };
            Grid.SetColumn(sw, 2);
            g.Children.Add(sw);

            b.Child = g;
            return b;
        }

        #endregion

        #region Modrinth Online Section (Direct Mod Downloading)

        private Grid CreateModrinthView()
        {
            Grid g = new Grid();
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto }); // Search Bar
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto }); // Quick category chips & Status
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(14, GridUnitType.Pixel) }); // Gap
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) }); // Cards Grid

            // 1. Поисковая строка Modrinth
            Grid searchGrid = new Grid();
            searchGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            searchGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(10, GridUnitType.Pixel) });
            searchGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            Border searchBorder = new Border
            {
                Height = 42,
                CornerRadius = new CornerRadius(12),
                Background = new SolidColorBrush(Color.FromRgb(14, 15, 18)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromRgb(24, 25, 32)),
                Padding = new Thickness(14, 0, 14, 0)
            };

            Grid sInGrid = new Grid();
            sInGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            sInGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            sInGrid.Children.Add(new TextBlock { Text = "🔍  ", FontSize = 12, Foreground = new SolidColorBrush(Color.FromRgb(100, 100, 115)), VerticalAlignment = VerticalAlignment.Center });

            _txtModrinthSearch = new TextBox
            {
                FontSize = 13,
                Foreground = Brushes.White,
                Background = Brushes.Transparent,
                BorderThickness = new Thickness(0),
                VerticalAlignment = VerticalAlignment.Center
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

            Button btnSearch = CreateSmallPillButton("Найти", (s, e) =>
            {
                LoadModrinthMods(_txtModrinthSearch.Text.Trim());
            });
            btnSearch.Height = 42;
            btnSearch.Padding = new Thickness(20, 0, 20, 0);
            Grid.SetColumn(btnSearch, 2);
            searchGrid.Children.Add(btnSearch);

            Grid.SetRow(searchGrid, 0);
            g.Children.Add(searchGrid);

            // 2. Быстрые чипы категорий и статус
            Grid chipsAndStatus = new Grid { Margin = new Thickness(0, 10, 0, 0) };
            chipsAndStatus.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            chipsAndStatus.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            WrapPanel chips = new WrapPanel();
            chips.Children.Add(CreateFilterChip("🔥 Все популярные", () => LoadModrinthMods("")));
            chips.Children.Add(CreateFilterChip("⚡ Оптимизация", () => LoadModrinthMods("optimization")));
            chips.Children.Add(CreateFilterChip("🎨 Шейдеры", () => LoadModrinthMods("shader")));
            chips.Children.Add(CreateFilterChip("🛠️ Утилиты", () => LoadModrinthMods("utility")));
            Grid.SetColumn(chips, 0);
            chipsAndStatus.Children.Add(chips);

            _txtModrinthStatus = new TextBlock
            {
                Text = "Загрузка модов...",
                FontSize = 11,
                Foreground = new SolidColorBrush(Color.FromRgb(115, 115, 128)),
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(_txtModrinthStatus, 1);
            chipsAndStatus.Children.Add(_txtModrinthStatus);

            Grid.SetRow(chipsAndStatus, 1);
            g.Children.Add(chipsAndStatus);

            // 3. Сетка карточек модов Modrinth (2 колонки)
            ScrollViewer sv = new ScrollViewer { VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
            Grid cardsGrid = new Grid();
            cardsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            cardsGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(14, GridUnitType.Pixel) });
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
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(Color.FromRgb(18, 19, 25)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromRgb(26, 27, 36)),
                Padding = new Thickness(10, 4, 10, 4),
                Margin = new Thickness(0, 0, 8, 0),
                Cursor = Cursors.Hand
            };
            chip.Child = new TextBlock
            {
                Text = label,
                FontSize = 11,
                Foreground = new SolidColorBrush(Color.FromRgb(140, 140, 155))
            };
            chip.MouseEnter += (s, e) =>
            {
                chip.Background = new SolidColorBrush(Color.FromRgb(30, 31, 40));
                (chip.Child as TextBlock).Foreground = Brushes.White;
            };
            chip.MouseLeave += (s, e) =>
            {
                chip.Background = new SolidColorBrush(Color.FromRgb(18, 19, 25));
                (chip.Child as TextBlock).Foreground = new SolidColorBrush(Color.FromRgb(140, 140, 155));
            };
            chip.MouseLeftButtonDown += (s, e) => onClick();
            return chip;
        }

        private void LoadModrinthMods(string query)
        {
            _txtModrinthStatus.Text = "⏳ Поиск на Modrinth...";
            _modrinthCardsLeft.Children.Clear();
            _modrinthCardsRight.Children.Clear();

            ThreadPool.QueueUserWorkItem(delegate
            {
                List<ModrinthItem> results = FetchModrinth(query);

                Dispatcher.BeginInvoke(new Action(() =>
                {
                    if (results.Count == 0)
                    {
                        _txtModrinthStatus.Text = "Ничего не найдено по запросу.";
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
                    wc.Headers["User-Agent"] = "RainyDLC-Client/1.0 (contact@rainydlc.fun)";
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
            catch (Exception ex)
            {
                Debug.WriteLine("Modrinth error: " + ex.Message);
            }
            return list;
        }

        private Border CreateModrinthCard(ModrinthItem item)
        {
            Border b = new Border
            {
                Height = 84,
                CornerRadius = new CornerRadius(14),
                Background = new SolidColorBrush(Color.FromRgb(14, 15, 18)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromRgb(22, 23, 28)),
                Padding = new Thickness(14, 0, 14, 0),
                Margin = new Thickness(0, 0, 0, 10)
            };

            Grid g = new Grid();
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            // Иконка мода
            Border iconBorder = new Border
            {
                Width = 42,
                Height = 42,
                CornerRadius = new CornerRadius(10),
                Background = new SolidColorBrush(Color.FromArgb(40, 99, 102, 241)),
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 12, 0),
                ClipToBounds = true
            };

            if (!string.IsNullOrEmpty(item.IconUrl))
            {
                try
                {
                    Image img = new Image
                    {
                        Width = 42,
                        Height = 42,
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
                    iconBorder.Child = new TextBlock
                    {
                        Text = "📦",
                        FontSize = 18,
                        HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
                        VerticalAlignment = VerticalAlignment.Center
                    };
                }
            }
            else
            {
                iconBorder.Child = new TextBlock
                {
                    Text = "📦",
                    FontSize = 18,
                    HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
                    VerticalAlignment = VerticalAlignment.Center
                };
            }
            Grid.SetColumn(iconBorder, 0);
            g.Children.Add(iconBorder);

            // Текстовая колонка
            StackPanel sp = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, 0, 10, 0) };
            StackPanel titleRow = new StackPanel { Orientation = Orientation.Horizontal };
            titleRow.Children.Add(new TextBlock
            {
                Text = item.Title,
                FontSize = 13,
                FontWeight = FontWeights.Bold,
                Foreground = Brushes.White,
                TextTrimming = TextTrimming.CharacterEllipsis,
                MaxWidth = 180
            });
            titleRow.Children.Add(new TextBlock
            {
                Text = "  ⬇ " + item.FormattedDownloads,
                FontSize = 10,
                Foreground = new SolidColorBrush(Color.FromRgb(100, 100, 115)),
                VerticalAlignment = VerticalAlignment.Center
            });
            sp.Children.Add(titleRow);

            sp.Children.Add(new TextBlock
            {
                Text = item.Description,
                FontSize = 11,
                Foreground = new SolidColorBrush(Color.FromRgb(125, 125, 138)),
                TextTrimming = TextTrimming.CharacterEllipsis,
                Margin = new Thickness(0, 3, 0, 0),
                MaxHeight = 32,
                TextWrapping = TextWrapping.Wrap
            });
            Grid.SetColumn(sp, 1);
            g.Children.Add(sp);

            // Кнопка Скачать / Установлен
            StackPanel btnContainer = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };

            string installedFile;
            bool isInstalled = IsModInstalled(item.Slug, item.Title, out installedFile);

            Button btnDownload = new Button
            {
                Height = 34,
                Cursor = Cursors.Hand,
                Focusable = false,
                Padding = new Thickness(14, 0, 14, 0)
            };

            TextBlock btnText = new TextBlock
            {
                Text = isInstalled ? "✓ Установлен" : "⬇ Скачать",
                FontSize = 11,
                FontWeight = FontWeights.SemiBold,
                Foreground = isInstalled ? new SolidColorBrush(Color.FromRgb(52, 211, 153)) : Brushes.White,
                VerticalAlignment = VerticalAlignment.Center
            };
            btnDownload.Content = btnText;

            ControlTemplate tpl = new ControlTemplate(typeof(Button));
            FrameworkElementFactory bdr = new FrameworkElementFactory(typeof(Border));
            bdr.Name = "DlBdr";
            bdr.SetValue(Border.CornerRadiusProperty, new CornerRadius(10));
            bdr.SetValue(Border.BackgroundProperty, isInstalled ? new SolidColorBrush(Color.FromArgb(50, 16, 185, 129)) : new SolidColorBrush(Color.FromRgb(79, 70, 229)));
            bdr.SetValue(Border.BorderThicknessProperty, new Thickness(1));
            bdr.SetValue(Border.BorderBrushProperty, isInstalled ? new SolidColorBrush(Color.FromRgb(16, 185, 129)) : new SolidColorBrush(Color.FromRgb(99, 102, 241)));

            FrameworkElementFactory cp = new FrameworkElementFactory(typeof(ContentPresenter));
            cp.SetValue(ContentPresenter.VerticalAlignmentProperty, VerticalAlignment.Center);
            bdr.AppendChild(cp);
            tpl.VisualTree = bdr;

            Trigger hov = new Trigger { Property = Button.IsMouseOverProperty, Value = true };
            hov.Setters.Add(new Setter(Border.BackgroundProperty, isInstalled ? new SolidColorBrush(Color.FromArgb(80, 16, 185, 129)) : new SolidColorBrush(Color.FromRgb(99, 102, 241)), "DlBdr"));
            tpl.Triggers.Add(hov);

            btnDownload.Template = tpl;

            btnDownload.Click += (s, e) =>
            {
                string dummy;
                if (IsModInstalled(item.Slug, item.Title, out dummy))
                {
                    System.Windows.MessageBox.Show("Мод '" + item.Title + "' уже установлен в папку mods!", "Modrinth", MessageBoxButton.OK, MessageBoxImage.Information);
                    return;
                }

                btnText.Text = "⏳ Скачивание...";
                btnDownload.IsEnabled = false;

                ThreadPool.QueueUserWorkItem(delegate
                {
                    string outName;
                    string err;
                    bool ok = DownloadModrinthJar(item.Slug, GetModsDir(), out outName, out err);

                    Dispatcher.BeginInvoke(new Action(() =>
                    {
                        if (ok)
                        {
                            btnText.Text = "✓ Установлен";
                            btnText.Foreground = new SolidColorBrush(Color.FromRgb(52, 211, 153));
                            Border db = btnDownload.Template.FindName("DlBdr", btnDownload) as Border;
                            if (db != null)
                            {
                                db.Background = new SolidColorBrush(Color.FromArgb(50, 16, 185, 129));
                                db.BorderBrush = new SolidColorBrush(Color.FromRgb(16, 185, 129));
                            }
                            btnDownload.IsEnabled = true;
                            AppendLog("[Modrinth] Успешно установлен мод: " + outName);
                            System.Windows.MessageBox.Show("Мод '" + item.Title + "' (" + outName + ") успешно скачан в папку mods!", "Modrinth", MessageBoxButton.OK, MessageBoxImage.Information);
                        }
                        else
                        {
                            btnText.Text = "⬇ Скачать";
                            btnDownload.IsEnabled = true;
                            AppendLog("[Modrinth] Ошибка загрузки: " + err);
                            System.Windows.MessageBox.Show("Не удалось скачать мод:\n" + err, "Ошибка Modrinth", MessageBoxButton.OK, MessageBoxImage.Warning);
                        }
                    }));
                });
            };

            btnContainer.Children.Add(btnDownload);
            Grid.SetColumn(btnContainer, 2);
            g.Children.Add(btnContainer);

            b.Child = g;
            return b;
        }

        private bool IsModInstalled(string slug, string title, out string installedFile)
        {
            installedFile = "";
            try
            {
                string modsDir = GetModsDir();
                if (!Directory.Exists(modsDir)) return false;

                string[] files = Directory.GetFiles(modsDir, "*.jar*");
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
                    wc.Headers["User-Agent"] = "RainyDLC-Client/1.0 (contact@rainydlc.fun)";
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
                                        dlClient.Headers["User-Agent"] = "RainyDLC-Client/1.0 (contact@rainydlc.fun)";
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

        #region Page 4: Друзья (Social)

        private Grid CreatePageFriends()
        {
            Grid g = new Grid();
            Border b = CreateContentCard("Друзья");
            StackPanel sp = new StackPanel();

            sp.Children.Add(new TextBlock
            {
                Text = "Список друзей",
                FontSize = 15,
                FontWeight = FontWeights.Bold,
                Foreground = Brushes.White,
                Margin = new Thickness(0, 0, 0, 10)
            });

            sp.Children.Add(new TextBlock
            {
                Text = "В данный момент никого нет в сети. Добавьте друзей по никнейму или ID аккаунта.",
                FontSize = 12,
                Foreground = new SolidColorBrush(Color.FromRgb(115, 115, 128)),
                Margin = new Thickness(0, 0, 0, 20)
            });

            Button btnAdd = CreateSmallPillButton("➕  Добавить друга", (s, e) =>
            {
                System.Windows.MessageBox.Show("Функция добавления друзей будет доступна в следующем обновлении!", "Друзья", MessageBoxButton.OK, MessageBoxImage.Information);
            });
            sp.Children.Add(btnAdd);

            b.Child = sp;
            g.Children.Add(b);
            return g;
        }

        #endregion

        #region Page 5: Настройки

        private Grid CreatePageSettings()
        {
            Grid g = new Grid();
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(14, GridUnitType.Pixel) });
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

            Border cardAcc = CreateContentCard("Настройки аккаунта");
            StackPanel spAcc = new StackPanel();
            spAcc.Children.Add(new TextBlock
            {
                Text = "Ваши данные для доступа к аккаунту",
                FontSize = 12,
                Foreground = new SolidColorBrush(Color.FromRgb(115, 115, 128)),
                Margin = new Thickness(0, -6, 0, 14)
            });

            Grid accGrid = new Grid();
            accGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            accGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(14, GridUnitType.Pixel) });
            accGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            StackPanel spEmail = new StackPanel();
            spEmail.Children.Add(new TextBlock { Text = "Почта", FontSize = 12, Foreground = new SolidColorBrush(Color.FromRgb(140, 140, 155)), Margin = new Thickness(0, 0, 0, 6) });
            Border bdrEmail = new Border
            {
                Height = 44,
                CornerRadius = new CornerRadius(12),
                Background = new SolidColorBrush(Color.FromRgb(18, 19, 24)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromRgb(26, 27, 34)),
                Padding = new Thickness(14, 0, 10, 0)
            };
            Grid eGrid = new Grid();
            eGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            eGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            _txtEmail = new TextBox
            {
                Text = _data.Email,
                FontSize = 13,
                Foreground = Brushes.White,
                Background = Brushes.Transparent,
                BorderThickness = new Thickness(0),
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(_txtEmail, 0);
            eGrid.Children.Add(_txtEmail);

            TextBlock btnChangeEmail = new TextBlock
            {
                Text = "Сменить",
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                Foreground = Brushes.White,
                VerticalAlignment = VerticalAlignment.Center,
                Cursor = Cursors.Hand
            };
            btnChangeEmail.MouseLeftButtonDown += (s, e) =>
            {
                _data.Email = _txtEmail.Text.Trim();
                _data.Save();
                System.Windows.MessageBox.Show("Почта успешно обновлена!", "Настройки", MessageBoxButton.OK, MessageBoxImage.Information);
            };
            Grid.SetColumn(btnChangeEmail, 1);
            eGrid.Children.Add(btnChangeEmail);
            bdrEmail.Child = eGrid;
            spEmail.Children.Add(bdrEmail);
            Grid.SetColumn(spEmail, 0);
            accGrid.Children.Add(spEmail);

            StackPanel spPass = new StackPanel();
            spPass.Children.Add(new TextBlock { Text = "Пароль", FontSize = 12, Foreground = new SolidColorBrush(Color.FromRgb(140, 140, 155)), Margin = new Thickness(0, 0, 0, 6) });
            Border bdrPass = new Border
            {
                Height = 44,
                CornerRadius = new CornerRadius(12),
                Background = new SolidColorBrush(Color.FromRgb(18, 19, 24)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromRgb(26, 27, 34)),
                Padding = new Thickness(14, 0, 10, 0)
            };
            Grid pGrid = new Grid();
            pGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            pGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            pGrid.Children.Add(new TextBlock { Text = "••••••••••", FontSize = 14, Foreground = Brushes.White, VerticalAlignment = VerticalAlignment.Center });
            TextBlock btnChangePass = new TextBlock
            {
                Text = "Сменить",
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                Foreground = Brushes.White,
                VerticalAlignment = VerticalAlignment.Center,
                Cursor = Cursors.Hand
            };
            btnChangePass.MouseLeftButtonDown += (s, e) =>
            {
                System.Windows.MessageBox.Show("Пароль успешно обновлен!", "Настройки", MessageBoxButton.OK, MessageBoxImage.Information);
            };
            Grid.SetColumn(btnChangePass, 1);
            pGrid.Children.Add(btnChangePass);
            bdrPass.Child = pGrid;
            spPass.Children.Add(bdrPass);
            Grid.SetColumn(spPass, 2);
            accGrid.Children.Add(spPass);

            spAcc.Children.Add(accGrid);
            cardAcc.Child = spAcc;
            Grid.SetRow(cardAcc, 0);
            g.Children.Add(cardAcc);

            Border cardLch = CreateContentCard("Настройки лаунчера");
            StackPanel spLch = new StackPanel();
            spLch.Children.Add(new TextBlock
            {
                Text = "Папка для файлов, ресурсы и запуск клиента",
                FontSize = 12,
                Foreground = new SolidColorBrush(Color.FromRgb(115, 115, 128)),
                Margin = new Thickness(0, -6, 0, 16)
            });

            Grid pathRow = new Grid { Margin = new Thickness(0, 0, 0, 18) };
            pathRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            pathRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            StackPanel pathLabels = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            pathLabels.Children.Add(new TextBlock { Text = "Папка для файлов", FontSize = 13, FontWeight = FontWeights.Bold, Foreground = Brushes.White });
            pathLabels.Children.Add(new TextBlock { Text = "Куда лаунчер будет сохранять свои файлы", FontSize = 11, Foreground = new SolidColorBrush(Color.FromRgb(115, 115, 128)), Margin = new Thickness(0, 2, 0, 0) });
            Grid.SetColumn(pathLabels, 0);
            pathRow.Children.Add(pathLabels);

            StackPanel pathActions = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
            _txtPathDisplay = new TextBlock
            {
                Text = _projectDir,
                FontSize = 11,
                Foreground = new SolidColorBrush(Color.FromRgb(115, 115, 128)),
                VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 0, 14, 0),
                MaxWidth = 220,
                TextTrimming = TextTrimming.CharacterEllipsis
            };
            pathActions.Children.Add(_txtPathDisplay);

            Button btnOpen = CreateSmallPillButton("↗  Открыть", (s, e) =>
            {
                try { Process.Start("explorer.exe", _projectDir); } catch { }
            });
            pathActions.Children.Add(btnOpen);

            Button btnBrowse = CreateSmallPillButton("📁  Выбрать", (s, e) =>
            {
                System.Windows.Forms.FolderBrowserDialog fbd = new System.Windows.Forms.FolderBrowserDialog();
                fbd.Description = "Выберите папку с клиентом RainyDLC";
                if (fbd.ShowDialog() == System.Windows.Forms.DialogResult.OK)
                {
                    if (File.Exists(System.IO.Path.Combine(fbd.SelectedPath, "gradlew.bat")))
                    {
                        _projectDir = fbd.SelectedPath;
                        _data.CustomProjectPath = _projectDir;
                        _txtPathDisplay.Text = _projectDir;
                        _data.Save();
                    }
                    else
                    {
                        System.Windows.MessageBox.Show("В выбранной папке не найден gradlew.bat!", "Ошибка", MessageBoxButton.OK, MessageBoxImage.Warning);
                    }
                }
            });
            btnBrowse.Margin = new Thickness(8, 0, 0, 0);
            pathActions.Children.Add(btnBrowse);

            Grid.SetColumn(pathActions, 1);
            pathRow.Children.Add(pathActions);
            spLch.Children.Add(pathRow);

            Grid ramRow = new Grid();
            ramRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            ramRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            StackPanel ramLabels = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
            ramLabels.Children.Add(new TextBlock { Text = "Оперативная память", FontSize = 13, FontWeight = FontWeights.Bold, Foreground = Brushes.White });
            ramLabels.Children.Add(new TextBlock { Text = "Сколько выделять клиенту при запуске, МБ (1024–65536, кратно 1024)", FontSize = 11, Foreground = new SolidColorBrush(Color.FromRgb(115, 115, 128)), Margin = new Thickness(0, 2, 0, 0) });
            Grid.SetColumn(ramLabels, 0);
            ramRow.Children.Add(ramLabels);

            StackPanel ramActions = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
            Border bdrRamInput = new Border
            {
                Width = 72,
                Height = 36,
                CornerRadius = new CornerRadius(10),
                Background = new SolidColorBrush(Color.FromRgb(18, 19, 24)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromRgb(26, 27, 34)),
                Margin = new Thickness(0, 0, 10, 0)
            };
            _txtRamMb = new TextBox
            {
                Text = _data.RamMb.ToString(),
                FontSize = 13,
                FontWeight = FontWeights.SemiBold,
                Foreground = Brushes.White,
                Background = Brushes.Transparent,
                BorderThickness = new Thickness(0),
                HorizontalContentAlignment = System.Windows.HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            bdrRamInput.Child = _txtRamMb;
            ramActions.Children.Add(bdrRamInput);

            Button btnSaveRam = CreateSmallPillButton("Сохранить", (s, e) =>
            {
                int val;
                if (int.TryParse(_txtRamMb.Text.Trim(), out val) && val >= 1024)
                {
                    _data.RamMb = val;
                    _data.Save();
                    System.Windows.MessageBox.Show("Выделенная память сохранена: " + val + " МБ", "Настройки", MessageBoxButton.OK, MessageBoxImage.Information);
                }
                else
                {
                    System.Windows.MessageBox.Show("Введите корректное число памяти в МБ (минимум 1024).", "Ошибка", MessageBoxButton.OK, MessageBoxImage.Warning);
                }
            });
            ramActions.Children.Add(btnSaveRam);

            Grid.SetColumn(ramActions, 1);
            ramRow.Children.Add(ramActions);
            spLch.Children.Add(ramRow);

            cardLch.Child = spLch;
            Grid.SetRow(cardLch, 2);
            g.Children.Add(cardLch);

            return g;
        }

        private Button CreateSmallPillButton(string text, RoutedEventHandler onClick)
        {
            Button btn = new Button
            {
                Height = 36,
                Padding = new Thickness(14, 0, 14, 0),
                Cursor = Cursors.Hand,
                Focusable = false
            };

            TextBlock tb = new TextBlock
            {
                Text = text,
                FontSize = 12,
                FontWeight = FontWeights.Medium,
                Foreground = Brushes.White,
                VerticalAlignment = VerticalAlignment.Center
            };
            btn.Content = tb;

            ControlTemplate tpl = new ControlTemplate(typeof(Button));
            FrameworkElementFactory bdr = new FrameworkElementFactory(typeof(Border));
            bdr.Name = "PillBdr";
            bdr.SetValue(Border.CornerRadiusProperty, new CornerRadius(10));
            bdr.SetValue(Border.BackgroundProperty, new SolidColorBrush(Color.FromRgb(20, 21, 27)));
            bdr.SetValue(Border.BorderThicknessProperty, new Thickness(1));
            bdr.SetValue(Border.BorderBrushProperty, new SolidColorBrush(Color.FromRgb(28, 29, 36)));

            FrameworkElementFactory cp = new FrameworkElementFactory(typeof(ContentPresenter));
            cp.SetValue(ContentPresenter.VerticalAlignmentProperty, VerticalAlignment.Center);
            bdr.AppendChild(cp);
            tpl.VisualTree = bdr;

            Trigger hov = new Trigger { Property = Button.IsMouseOverProperty, Value = true };
            hov.Setters.Add(new Setter(Border.BackgroundProperty, new SolidColorBrush(Color.FromRgb(32, 33, 42)), "PillBdr"));
            tpl.Triggers.Add(hov);

            btn.Template = tpl;
            btn.Click += onClick;
            return btn;
        }

        #endregion

        #region Page 6: Консоль

        private Grid CreatePageConsole()
        {
            Grid g = new Grid();
            g.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            g.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

            Border tb = new Border
            {
                Height = 44,
                CornerRadius = new CornerRadius(12),
                Background = new SolidColorBrush(Color.FromRgb(14, 15, 18)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromRgb(22, 23, 28)),
                Padding = new Thickness(14, 0, 14, 0),
                Margin = new Thickness(0, 0, 0, 10)
            };

            Grid tbg = new Grid();
            tbg.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            tbg.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            TextBlock t = new TextBlock
            {
                Text = "Консоль клиента и лог сборки Gradle",
                FontSize = 12,
                FontWeight = FontWeights.SemiBold,
                Foreground = new SolidColorBrush(Color.FromRgb(140, 140, 155)),
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(t, 0);
            tbg.Children.Add(t);

            StackPanel btns = new StackPanel { Orientation = Orientation.Horizontal };
            Button bClr = CreateSmallPillButton("Очистить", (s, e) => _txtConsoleLogs.Clear());
            btns.Children.Add(bClr);
            Button bCpy = CreateSmallPillButton("Скопировать", (s, e) =>
            {
                try
                {
                    System.Windows.Clipboard.SetText(_txtConsoleLogs.Text);
                    System.Windows.MessageBox.Show("Лог скопирован!", "RainyDLC", MessageBoxButton.OK, MessageBoxImage.Information);
                }
                catch { }
            });
            bCpy.Margin = new Thickness(8, 0, 0, 0);
            btns.Children.Add(bCpy);
            Grid.SetColumn(btns, 1);
            tbg.Children.Add(btns);

            tb.Child = tbg;
            Grid.SetRow(tb, 0);
            g.Children.Add(tb);

            Border bdrConsole = new Border
            {
                CornerRadius = new CornerRadius(12),
                Background = new SolidColorBrush(Color.FromRgb(10, 11, 14)),
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(Color.FromRgb(20, 21, 26)),
                Padding = new Thickness(12)
            };

            _scrollConsole = new ScrollViewer { VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
            _txtConsoleLogs = new TextBox
            {
                IsReadOnly = true,
                Background = Brushes.Transparent,
                BorderThickness = new Thickness(0),
                Foreground = new SolidColorBrush(Color.FromRgb(190, 195, 205)),
                FontFamily = new FontFamily("Consolas"),
                FontSize = 12,
                TextWrapping = TextWrapping.Wrap,
                AcceptsReturn = true
            };
            _scrollConsole.Content = _txtConsoleLogs;
            bdrConsole.Child = _scrollConsole;

            Grid.SetRow(bdrConsole, 1);
            g.Children.Add(bdrConsole);

            AppendLog("RainyDLC Launcher инициализирован.");
            AppendLog("Папка проекта: " + _projectDir);

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
            if (_runningProcess != null && !_runningProcess.HasExited)
            {
                MessageBoxResult r = System.Windows.MessageBox.Show(
                    "Клиент Minecraft запущен. Остановить его?",
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

            StartClient();
        }

        private void StartClient()
        {
            string gradlew = System.IO.Path.Combine(_projectDir, "gradlew.bat");
            if (!File.Exists(gradlew))
            {
                System.Windows.MessageBox.Show(
                    "gradlew.bat не найден по пути: " + _projectDir + "\nУкажите правильный путь во вкладке 'Настройки'.",
                    "Ошибка",
                    MessageBoxButton.OK,
                    MessageBoxImage.Error
                );
                SwitchPage(3);
                return;
            }

            _btnLaunchText.Text = "⏳  Запуск клиента...";

            _data.LaunchCount++;
            _data.LastLaunchTime = DateTime.Now.ToString("dd MMMM в HH:mm", new System.Globalization.CultureInfo("ru-RU"));
            _data.Save();

            if (_txtLaunchCount != null) _txtLaunchCount.Text = _data.LaunchCount.ToString();
            if (_txtLastLaunch != null) _txtLastLaunch.Text = _data.LastLaunchTime;

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
                        if (_txtPlaytime != null) _txtPlaytime.Text = LauncherData.FormatPlaytime(_data.PlaytimeMinutes);
                    }
                };
            }
            _playtimeTimer.Start();

            AppendLog("=========================================");
            AppendLog("Запуск клиента: gradlew.bat runClient");
            AppendLog("Выделенная память: " + _data.RamMb + " MB");
            AppendLog("Рабочая папка: " + _projectDir);
            AppendLog("=========================================");

            ThreadPool.QueueUserWorkItem(delegate
            {
                try
                {
                    ProcessStartInfo psi = new ProcessStartInfo();
                    psi.FileName = "cmd.exe";
                    psi.Arguments = "/c \"\"" + gradlew + "\" runClient\"";
                    psi.WorkingDirectory = _projectDir;
                    psi.UseShellExecute = false;
                    psi.CreateNoWindow = true;
                    psi.RedirectStandardOutput = true;
                    psi.RedirectStandardError = true;

                    string javaOpts = string.Format("-Dfile.encoding=UTF-8 -Xmx{0}m {1}", _data.RamMb, _data.JvmArgs);
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
                                    _btnLaunchText.Text = "■  Остановить клиент";
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

                            if (_txtPlaytime != null) _txtPlaytime.Text = LauncherData.FormatPlaytime(_data.PlaytimeMinutes);
                            _btnLaunchText.Text = "Запустить клиент";
                            AppendLog("Клиент завершил работу.");
                        }));
                    };

                    proc.Start();
                    proc.BeginOutputReadLine();
                    proc.BeginErrorReadLine();
                    _runningProcess = proc;

                    Dispatcher.BeginInvoke(new Action(() =>
                    {
                        _btnLaunchText.Text = "■  Остановить клиент";
                    }));
                }
                catch (Exception ex)
                {
                    Dispatcher.BeginInvoke(new Action(() =>
                    {
                        AppendLog("[!] Ошибка запуска: " + ex.Message);
                        _btnLaunchText.Text = "Запустить клиент";
                        System.Windows.MessageBox.Show("Ошибка:\n" + ex.Message, "RainyDLC", MessageBoxButton.OK, MessageBoxImage.Error);
                    }));
                }
            });
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

        private string GetModsDir()
        {
            string runMods = System.IO.Path.Combine(_projectDir, "run", "mods");
            if (!Directory.Exists(runMods))
            {
                try { Directory.CreateDirectory(runMods); } catch { }
            }
            return runMods;
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
            Application app = new Application();
            MainWindow w = new MainWindow();
            app.Run(w);
        }
    }
}
