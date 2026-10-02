using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;

namespace RMF.Windows.Services;

public class GoogleUserInfo
{
    [JsonPropertyName("sub")]
    public string Sub { get; set; } = string.Empty;

    [JsonPropertyName("name")]
    public string Name { get; set; } = string.Empty;

    [JsonPropertyName("given_name")]
    public string GivenName { get; set; } = string.Empty;

    [JsonPropertyName("family_name")]
    public string FamilyName { get; set; } = string.Empty;

    [JsonPropertyName("picture")]
    public string Picture { get; set; } = string.Empty;

    [JsonPropertyName("email")]
    public string Email { get; set; } = string.Empty;

    [JsonPropertyName("email_verified")]
    public bool EmailVerified { get; set; } = false;
}

public class GoogleTokenResponse
{
    [JsonPropertyName("access_token")]
    public string AccessToken { get; set; } = string.Empty;

    [JsonPropertyName("refresh_token")]
    public string RefreshToken { get; set; } = string.Empty;

    [JsonPropertyName("expires_in")]
    public int ExpiresIn { get; set; } = 3600;

    [JsonPropertyName("token_type")]
    public string TokenType { get; set; } = "Bearer";

    [JsonPropertyName("scope")]
    public string Scope { get; set; } = string.Empty;
}

public static class GoogleAuthService
{
    public const string RedirectUri = "http://127.0.0.1:58432/";
    private static readonly HttpClient HttpClient = new HttpClient();

    /// <summary>
    /// 执行真实 Google OAuth 2.0 桌面回环授权登录 (Desktop Loopback Flow)
    /// </summary>
    public static async Task<(bool success, string message, GoogleUserInfo? user)> SignInWithOAuthAsync(
        string clientId, 
        string clientSecret, 
        Action<string>? statusCallback = null,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(clientId))
        {
            return (false, "请先填写 Google Cloud Client ID", null);
        }

        clientId = clientId.Trim();
        clientSecret = (clientSecret ?? string.Empty).Trim();

        HttpListener? listener = null;
        try
        {
            statusCallback?.Invoke("正在启动本地授权监听器 (127.0.0.1:58432)...");
            listener = new HttpListener();
            listener.Prefixes.Add(RedirectUri);
            listener.Start();

            // 构造 Google 官方 OAuth 授权 URL
            // 包含个人资料、邮箱，以及日历只读与 Drive AppData 权限
            string scopes = Uri.EscapeDataString("openid profile email https://www.googleapis.com/auth/calendar.readonly https://www.googleapis.com/auth/drive.appdata");
            string state = Guid.NewGuid().ToString("N");
            string authUrl = $"https://accounts.google.com/o/oauth2/v2/auth?" +
                             $"client_id={Uri.EscapeDataString(clientId)}" +
                             $"&redirect_uri={Uri.EscapeDataString(RedirectUri)}" +
                             $"&response_type=code" +
                             $"&scope={scopes}" +
                             $"&state={state}" +
                             $"&access_type=offline" +
                             $"&prompt=consent%20select_account";

            statusCallback?.Invoke("已在默认浏览器打开 Google 登录授权页，请在浏览器中完成登录与授权...");

            // 启动系统浏览器
            Process.Start(new ProcessStartInfo
            {
                FileName = authUrl,
                UseShellExecute = true
            });

            // 等待浏览器回执 (最长等待 150 秒)
            using var cts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            cts.CancelAfter(TimeSpan.FromSeconds(150));

            var contextTask = listener.GetContextAsync();
            var completedTask = await Task.WhenAny(contextTask, Task.Delay(Timeout.Infinite, cts.Token));

            if (completedTask != contextTask)
            {
                listener.Stop();
                return (false, "Google 授权超时或已取消，请重试。", null);
            }

            var context = await contextTask;
            var req = context.Request;
            var res = context.Response;

            string? code = req.QueryString["code"];
            string? error = req.QueryString["error"];

            // 向浏览器回写精美友好的完成页面
            string responseHtml = @"
<!DOCTYPE html>
<html>
<head>
    <meta charset=""utf-8"">
    <title>Google 授权成功 - RMF</title>
    <style>
        body { background: #202124; color: #E8EAED; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display: flex; align-items: center; justify-content: center; height: 90vh; margin: 0; }
        .card { background: #28292C; border: 1px solid #3C4043; border-radius: 12px; padding: 36px 48px; text-align: center; max-width: 440px; box-shadow: 0 8px 24px rgba(0,0,0,0.4); }
        h2 { color: #8AB4F8; margin-top: 0; font-size: 22px; }
        p { color: #9AA0A6; font-size: 14px; line-height: 1.6; }
        .success-icon { font-size: 48px; margin-bottom: 12px; }
    </style>
</head>
<body>
    <div class=""card"">
        <div class=""success-icon"">🎉</div>
        <h2>Google 账号授权成功</h2>
        <p>已成功连接 RMF 个人工作台。你可以关闭此标签页并返回桌面客户端继续使用。</p>
    </div>
</body>
</html>";

            byte[] buffer = Encoding.UTF8.GetBytes(responseHtml);
            res.ContentLength64 = buffer.Length;
            res.ContentType = "text/html; charset=utf-8";
            await res.OutputStream.WriteAsync(buffer, 0, buffer.Length);
            res.OutputStream.Close();

            if (!string.IsNullOrWhiteSpace(error))
            {
                return (false, $"Google 返回错误: {error}", null);
            }

            if (string.IsNullOrWhiteSpace(code))
            {
                return (false, "未能在 Google 回调中获取到 authorization_code。", null);
            }

            statusCallback?.Invoke("正在使用 Authorization Code 交换 Google Access Token...");

            // 向 Google Token Endpoint 发起兑换
            var tokenReqData = new Dictionary<string, string>
            {
                ["code"] = code,
                ["client_id"] = clientId,
                ["client_secret"] = clientSecret,
                ["redirect_uri"] = RedirectUri,
                ["grant_type"] = "authorization_code"
            };

            using var tokenReq = new HttpRequestMessage(HttpMethod.Post, "https://oauth2.googleapis.com/token")
            {
                Content = new FormUrlEncodedContent(tokenReqData)
            };

            using var tokenRes = await HttpClient.SendAsync(tokenReq);
            string tokenJson = await tokenRes.Content.ReadAsStringAsync();

            if (!tokenRes.IsSuccessStatusCode)
            {
                return (false, $"交换 Token 失败: {tokenJson}", null);
            }

            var tokenData = JsonSerializer.Deserialize<GoogleTokenResponse>(tokenJson);
            if (tokenData == null || string.IsNullOrWhiteSpace(tokenData.AccessToken))
            {
                return (false, "未能解析 Google Token 响应数据。", null);
            }

            statusCallback?.Invoke("正在获取 Google 账号个人资料与头像...");

            // 获取用户个人资料
            using var userReq = new HttpRequestMessage(HttpMethod.Get, "https://www.googleapis.com/oauth2/v3/userinfo");
            userReq.Headers.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", tokenData.AccessToken);

            using var userRes = await HttpClient.SendAsync(userReq);
            string userJson = await userRes.Content.ReadAsStringAsync();

            if (!userRes.IsSuccessStatusCode)
            {
                return (false, $"获取用户信息失败: {userJson}", null);
            }

            var userInfo = JsonSerializer.Deserialize<GoogleUserInfo>(userJson);
            if (userInfo == null)
            {
                return (false, "未能解析 Google 用户资料。", null);
            }

            // 持久化存储至 ConfigService
            var config = ConfigService.Load();
            config.IsGoogleUserSignedIn = true;
            config.GoogleUserName = userInfo.Name;
            config.GoogleUserEmail = userInfo.Email;
            config.GoogleUserPictureUrl = userInfo.Picture;
            config.GoogleAccessToken = tokenData.AccessToken;
            if (!string.IsNullOrWhiteSpace(tokenData.RefreshToken))
            {
                config.GoogleRefreshToken = tokenData.RefreshToken;
            }
            config.GoogleClientId = clientId;
            config.GoogleClientSecret = clientSecret;
            config.IsGoogleDriveLinked = true;
            config.LastSyncTime = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
            ConfigService.Save(config);

            return (true, $"登录成功！欢迎你，{userInfo.Name} ({userInfo.Email})", userInfo);
        }
        catch (Exception ex)
        {
            return (false, $"登录过程异常: {ex.Message}", null);
        }
        finally
        {
            try
            {
                listener?.Stop();
                listener?.Close();
            }
            catch { }
        }
    }

    /// <summary>
    /// 退出 Google 账号登录
    /// </summary>
    public static void SignOut()
    {
        var config = ConfigService.Load();
        config.IsGoogleUserSignedIn = false;
        config.GoogleUserName = string.Empty;
        config.GoogleUserEmail = string.Empty;
        config.GoogleUserPictureUrl = string.Empty;
        config.GoogleAccessToken = string.Empty;
        config.GoogleRefreshToken = string.Empty;
        config.IsGoogleDriveLinked = false;
        ConfigService.Save(config);
    }

    /// <summary>
    /// 快速模拟/测试登录 (用于尚未配置 Google Cloud Client ID 时的即时体验)
    /// </summary>
    public static GoogleUserInfo MockSignIn(string name = "Renly", string email = "renly.rmf@gmail.com")
    {
        var config = ConfigService.Load();
        config.IsGoogleUserSignedIn = true;
        config.GoogleUserName = name;
        config.GoogleUserEmail = email;
        config.GoogleUserPictureUrl = "";
        config.LastSyncTime = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
        ConfigService.Save(config);

        return new GoogleUserInfo
        {
            Name = name,
            Email = email,
            EmailVerified = true
        };
    }
}
