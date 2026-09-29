<?php

error_reporting(0);
const
p    = "\033[1;97m",
m    = "\033[1;31m",
h    = "\033[1;32m",
k    = "\033[1;33m",
cyan = "\033[1;36m",
b    = "\033[1;34m",
mag  = "\033[1;35m",
bred = "\033[101m\033[1;37m",
n    = "\n",
t    = "\t",
r    = "                  \r",
d    = "\033[0m";

class Crypto {
    private string $iv     = 'dYQ9R99bkKLsLHad';
    private string $cipher = 'AES-256-CBC';
    private int    $flags  = OPENSSL_RAW_DATA;

    public function deriveKey(string $uid): string {
        $chars  = str_split($uid);
        $digits = [];
        foreach ($chars as $c) {
            if (ctype_digit($c)) $digits[] = (int)$c;
        }
        if (empty($digits)) return md5('defaultrollersid');
        $sum = array_sum($digits);
        $sorted = $digits;
        sort($sorted);
        $firstN = array_slice($chars, 0, count($sorted));
        $parts  = array_merge($sorted, $firstN);
        $parts[] = $sum;
        return md5(implode('', $parts));
    }

    public function encrypt($data, string $uid): string {
        $key = $this->deriveKey($uid);
        $plaintext = is_string($data) ? $data : json_encode($data, JSON_UNESCAPED_UNICODE);
        $encrypted = openssl_encrypt($plaintext, $this->cipher, $key, $this->flags, $this->iv);
        if ($encrypted === false) throw new RuntimeException('Encrypt gagal: ' . openssl_error_string());
        return base64_encode($encrypted);
    }

    public function decrypt(string $base64Data, string $uid): string {
        $key = $this->deriveKey($uid);
        $ciphertext = base64_decode($base64Data, true);
        if ($ciphertext === false) throw new RuntimeException('Decrypt gagal: base64 tidak valid');
        $decrypted = openssl_decrypt($ciphertext, $this->cipher, $key, $this->flags, $this->iv);
        if ($decrypted === false) throw new RuntimeException('Decrypt gagal: ' . openssl_error_string());
        return $decrypted;
    }
}

class WebSocket {
    private $socket   = null;
    private string $host;
    private string $path;

    public function connect(string $url, array $extraHeaders = []): void {
        if (!preg_match('/^wss?:\/\/([^\/?#]+)(\/[^?#]*)?(\?.*)?$/', $url, $m)) {
            throw new RuntimeException("URL WebSocket tidak valid: $url");
        }
        $this->host = $m[1];
        $this->path = ($m[2] ?? '/') . ($m[3] ?? '');
        $isSecure = str_starts_with($url, 'wss://');
        $port = $isSecure ? 443 : 80;
        $target = $isSecure ? "ssl://{$this->host}:$port" : "tcp://{$this->host}:$port";

        $ctx = stream_context_create(['ssl' => [
            'verify_peer'      => false,
            'verify_peer_name' => false,
        ]]);

        $this->socket = @stream_socket_client($target, $errno, $errstr, 30, STREAM_CLIENT_CONNECT, $ctx);
        if (!$this->socket) {
            throw new RuntimeException("WebSocket connect gagal: [$errno] $errstr");
        }

        stream_set_timeout($this->socket, 60);
        stream_set_blocking($this->socket, true);

        $wsKey    = base64_encode(random_bytes(16));
        $headers  = "GET {$this->path} HTTP/1.1\r\n";
        $headers .= "Host: {$this->host}\r\n";
        $headers .= "Upgrade: websocket\r\n";
        $headers .= "Connection: Upgrade\r\n";
        $headers .= "Sec-WebSocket-Key: $wsKey\r\n";
        $headers .= "Sec-WebSocket-Version: 13\r\n";
        $headers .= "Origin: https://rollercoin.com\r\n";
        $headers .= "User-Agent: Mozilla/5.0 (Linux; Android 10; Redmi Note 7) AppleWebKit/537.36 Chrome/146.0.0.0 Mobile Safari/537.36\r\n";
        foreach ($extraHeaders as $k => $v) {
            $headers .= "$k: $v\r\n";
        }
        $headers .= "\r\n";

        fwrite($this->socket, $headers);

        $response = '';
        while (!feof($this->socket)) {
            $line = fgets($this->socket, 4096);
            $response .= $line;
            if ($line === "\r\n") break;
        }

        if (!str_contains($response, '101')) {
            throw new RuntimeException("Handshake WebSocket gagal:\n$response");
        }

        $expectedAccept = base64_encode(sha1($wsKey . '258EAFA5-E914-47DA-95CA-C5AB0DC85B11', true));
        if (!str_contains($response, $expectedAccept)) {
            echo k . "  ⚠ Sec-WebSocket-Accept tidak cocok (lanjut tetap)" . d . n;
        }
    }

    public function send(string $data): void {
        if (!$this->socket) throw new RuntimeException("Socket tidak terhubung");
        $frame  = $this->buildFrame($data, 0x1);
        set_error_handler(null);
        $result = @fwrite($this->socket, $frame);
        restore_error_handler();
        if ($result === false || $result === 0) {
            $this->socket = null;
            throw new RuntimeException("Gagal kirim data ke WebSocket (koneksi terputus)");
        }
    }

    public function receive(int $timeoutSec = 30): ?string {
        if (!$this->socket) return null;
        stream_set_timeout($this->socket, $timeoutSec);
        return $this->readFrame();
    }

    public function close(): void {
        if ($this->socket) {
            $frame = $this->buildFrame('', 0x8);
            @fwrite($this->socket, $frame);
            fclose($this->socket);
            $this->socket = null;
        }
    }

    public function isConnected(): bool {
        return $this->socket !== null && !feof($this->socket);
    }

    private function buildFrame(string $payload, int $opcode): string {
        $len  = strlen($payload);
        $mask = random_bytes(4);
        $frame = chr(0x80 | ($opcode & 0x0F));
        if ($len <= 125) {
            $frame .= chr(0x80 | $len);
        } elseif ($len <= 0xFFFF) {
            $frame .= chr(0x80 | 126) . pack('n', $len);
        } else {
            $frame .= chr(0x80 | 127) . pack('J', $len);
        }
        $frame .= $mask;
        for ($i = 0; $i < $len; $i++) {
            $frame .= chr(ord($payload[$i]) ^ ord($mask[$i % 4]));
        }
        return $frame;
    }

    private function readFrame(): ?string {
        $b1 = $this->readBytes(1);
        if ($b1 === null) return null;
        $b2 = $this->readBytes(1);
        if ($b2 === null) return null;

        $byte1  = ord($b1);
        $byte2  = ord($b2);
        $opcode = $byte1 & 0x0F;
        $masked = ($byte2 & 0x80) !== 0;
        $len    = $byte2 & 0x7F;

        if ($len === 126) {
            $raw = $this->readBytes(2);
            if ($raw === null) return null;
            $len = unpack('n', $raw)[1];
        } elseif ($len === 127) {
            $raw = $this->readBytes(8);
            if ($raw === null) return null;
            $len = unpack('J', $raw)[1];
        }

        $maskKey = '';
        if ($masked) {
            $maskKey = $this->readBytes(4);
            if ($maskKey === null) return null;
        }

        $payload = '';
        if ($len > 0) {
            $payload = $this->readBytes($len);
            if ($payload === null) return null;
        }

        if ($masked && $maskKey) {
            $unmasked = '';
            for ($i = 0; $i < strlen($payload); $i++) {
                $unmasked .= chr(ord($payload[$i]) ^ ord($maskKey[$i % 4]));
            }
            $payload = $unmasked;
        }

        if ($opcode === 0x8) return null;
        if ($opcode === 0x9) {
            $pong = $this->buildFrame($payload, 0xA);
            fwrite($this->socket, $pong);
            return $this->readFrame();
        }
        if ($opcode === 0xA) return $this->readFrame();

        return $payload;
    }

    private function readBytes(int $n): ?string {
        $buf = '';
        $rem = $n;
        while ($rem > 0) {
            $chunk = fread($this->socket, $rem);
            if ($chunk === false || strlen($chunk) === 0) {
                $info = stream_get_meta_data($this->socket);
                if ($info['timed_out']) return null;
                if (feof($this->socket)) return null;
                usleep(5000);
                continue;
            }
            $buf .= $chunk;
            $rem -= strlen($chunk);
        }
        return $buf;
    }
}

class CaptchaSolver {
    private const SPINNER = ['⠋','⠙','⠹','⠸','⠼','⠴','⠦','⠧','⠇','⠏'];
    private int    $spinStep = 0;
    private string $cookieFile;
    private array  $config;

    public function __construct() {
        $this->config = [
            'fingerprint' => 'a22669a4ca3b38745bdb9fb563085882',
            'action' => 'auth',
            'csrf_token'  => 'a2c6d00e-49c7-479e-9965-1a3e0a0e',
            'user_agent'  => 'Mozilla/5.0 (Linux; Android 10; Redmi Note 7 Build/QKQ1.190910.002) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.7680.177 Mobile Safari/537.36',
        ];
        $this->cookieFile = tempnam(sys_get_temp_dir(), 'rc_cookie_');
        file_put_contents($this->cookieFile, "# Netscape HTTP Cookie File\n");
    }

    public function __destruct() {
        if (file_exists($this->cookieFile)) @unlink($this->cookieFile);
    }

    public function solve(): array {
        $attempt = 0;
        while (true) {
            $attempt++;
            $p = "[{$attempt}]";
            try {
                $info = $this->step("{$p} Ambil challenge", fn() => $this->getCaptchaStatus());
                if (isset($info['is_captcha_required']) && !$info['is_captcha_required']) {
                    echo "\n";
                    echo h . "  ✓ Captcha tidak diperlukan." . d . n;
                    return ['challenge' => null, 'points' => null, 'coordinates' => []];
                }
                $challenge = $info['challenge'];
                $maxDots = (int)($info['max_dots'] ?? 3);
                $captcha = $this->step("{$p} Buat captcha", fn() => $this->createCaptcha($challenge));
                $analysis = $this->step("{$p} Analisis gambar", fn() => $this->analyzeImage($captcha['captcha_img'], $captcha['thumb_img']));
                $targetN = $this->estimateTargetCount(array_values($analysis['scores']), $maxDots);
                $idxSubset = array_slice($analysis['sorted_indices'], 0, $targetN);
                $points = $this->buildPointsString($idxSubset, $analysis['clusters']);
                $coords = $this->buildCoordinatesArray($idxSubset, $analysis['clusters']);
                $result = $this->step("{$p} Kirim jawaban (n={$targetN})", fn() => $this->validateCaptcha($challenge, $points));
                if (!empty($result['data']['is_valid'])) {
                    return ['challenge' => $challenge, 'points' => $points, 'coordinates' => $coords];
                }
                echo "\r\033[K" . k . "  ✗ {$p} salah, retry..." . d;
                flush();
                sleep(1);
            } catch (RuntimeException $e) {
                echo "\r\033[K" . m . "  ✗ {$p} " . $e->getMessage() . d;
                flush();
                sleep(2);
            }
        }
    }

    private function request(string $method, string $url, array $headers, ?string $body = null): array {
        unset($headers['cookie']);
        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_FOLLOWLOCATION => false,
            CURLOPT_HTTPHEADER => array_map(fn($k, $v) => "$k: $v", array_keys($headers), $headers),
            CURLOPT_SSL_VERIFYPEER => false,
            CURLOPT_TIMEOUT => 30,
            CURLOPT_ENCODING => '',
            CURLOPT_HEADER => true,
            CURLOPT_COOKIEFILE => $this->cookieFile,
            CURLOPT_COOKIEJAR => $this->cookieFile,
        ]);
        if ($method === 'POST') {
            curl_setopt($ch, CURLOPT_POST, true);
            if ($body !== null) curl_setopt($ch, CURLOPT_POSTFIELDS, $body);
        }
        $raw   = curl_exec($ch);
        $code  = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $hSize = curl_getinfo($ch, CURLINFO_HEADER_SIZE);
        $err   = curl_error($ch);
        if ($err) throw new RuntimeException("cURL error: $err");
        return ['status' => $code, 'headers' => substr($raw, 0, $hSize), 'body' => substr($raw, $hSize)];
    }

    private function baseHeaders(string $host, array $extra = []): array {
        return array_merge([
            'host'             => $host,
            'user-agent'       => $this->config['user_agent'],
            'accept-language'  => 'id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7',
            'x-requested-with' => 'mark.via.gp',
        ], $extra);
    }

    private function decodeDataUri(string $s): string {
        return preg_match('/^data:image\/[^;]+;base64,(.+)$/s', $s, $m) ? base64_decode($m[1]) : base64_decode($s);
    }

    private function getCaptchaStatus(): array {
        $url = 'https://rollercoin.com/api/auth/captcha-status'.'?fingerprint=' . urlencode($this->config['fingerprint']) . '&action='. urlencode($this->config['action']);
        $res  = $this->request('GET', $url, $this->baseHeaders('rollercoin.com', [
            'accept' => 'application/json',
            'content-type' => 'application/json',
            'csrf-token' => $this->config['csrf_token'],
            'referer' => 'https://rollercoin.com/sign-in?welcome=true',
        ]));
        $data = json_decode($res['body'], true);
        if (!$data || !$data['success']) throw new RuntimeException("captcha-status gagal: " . $res['body']);
        return $data['data'];
    }

    private function createCaptcha(string $challenge): array {
        $body = json_encode(['challenge' => $challenge]);
        $res  = $this->request('POST', 'https://captcha.rollercoin.com/api/create-captcha',
            $this->baseHeaders('captcha.rollercoin.com', [
                'content-type' => 'application/json',
                'accept' => '*/*',
                'origin' => 'https://rollercoin.com',
                'referer' => 'https://rollercoin.com/',
            ]), $body);
        $decoded = @json_decode($res['body'], true);
        if (!$decoded)                               throw new RuntimeException("create-captcha: respons bukan JSON.");
        if (!isset($decoded['data']['captcha_img'])) throw new RuntimeException("create-captcha: captcha_img tidak ada.");
        if (!isset($decoded['data']['thumb_img']))   throw new RuntimeException("create-captcha: thumb_img tidak ada.");
        return [
            'captcha_img' => $this->decodeDataUri($decoded['data']['captcha_img']),
            'thumb_img' => $this->decodeDataUri($decoded['data']['thumb_img']),
        ];
    }

    private function validateCaptcha(string $challenge, string $points): array {
        $body = json_encode(['challenge' => $challenge, 'points' => $points]);
        $res  = $this->request('POST', 'https://captcha.rollercoin.com/api/validate-captcha',
            $this->baseHeaders('captcha.rollercoin.com', [
                'content-type' => 'application/json',
                'accept' => '*/*',
                'origin' => 'https://rollercoin.com',
                'referer' => 'https://rollercoin.com/',
            ]), $body);
        $data = @json_decode($res['body'], true);
        if (!$data) throw new RuntimeException("validate-captcha: respons bukan JSON.");
        return $data;
    }

    private function analyzeImage(string $captchaImgData, string $thumbImgData): array {
        $scene  = @imagecreatefromstring($captchaImgData);
        $thumbImg = @imagecreatefromstring($thumbImgData);
        if (!$scene) throw new RuntimeException("Gagal load captcha_img.");
        if (!$thumbImg) throw new RuntimeException("Gagal load thumb_img.");
        $scH = imagesy($scene);
        [$tBgR, $tBgG, $tBgB] = $this->detectBackground($thumbImg);
        $thumbFg = $this->extractForegroundPixels($thumbImg, $tBgR, $tBgG, $tBgB, 60);
        if (count($thumbFg) < 200) {
            $thumbFg = $this->extractForegroundPixels($thumbImg, $tBgR, $tBgG, $tBgB, 40);
        }
        if (empty($thumbFg)) throw new RuntimeException("Tidak ada foreground di thumb_img.");
        $thumbPixRgb = array_map(fn($p) => [$p['r'], $p['g'], $p['b']], $thumbFg);
        $thumbHist = $this->buildHsvHistogram($thumbPixRgb);
        $thumbAvgRgb = $this->avgRgb($thumbPixRgb);
        [$scBgR, $scBgG, $scBgB] = $this->detectBackground($scene);
        $sceneFg = $this->extractForegroundPixels($scene, $scBgR, $scBgG, $scBgB, 38);
        $clusters = $this->clusterPixels($sceneFg);
        if (empty($clusters)) throw new RuntimeException("Tidak ada blob terdeteksi di scene.");
        $scores = $this->scoreBlobs($clusters, $thumbHist, $thumbAvgRgb, $scH);
        arsort($scores);
        return ['clusters' => $clusters, 'scores' => $scores, 'sorted_indices' => array_keys($scores)];
    }

    private function detectBackground($img): array {
        $w = imagesx($img); $h = imagesy($img); $hist = [];
        for ($y = 0; $y < $h; $y++) {
            for ($x = 0; $x < $w; $x++) {
                $rgb = imagecolorat($img, $x, $y);
                $r = ($rgb >> 16) & 0xFF; $g = ($rgb >> 8) & 0xFF; $b = $rgb & 0xFF;
                $key = (($r >> 5) & 7) . '_' . (($g >> 5) & 7) . '_' . (($b >> 5) & 7);
                if (!isset($hist[$key])) $hist[$key] = ['count' => 0, 'rSum' => 0, 'gSum' => 0, 'bSum' => 0];
                $hist[$key]['count']++; $hist[$key]['rSum'] += $r; $hist[$key]['gSum'] += $g; $hist[$key]['bSum'] += $b;
            }
        }
        arsort($hist);
        $top = reset($hist);
        return [$top['rSum'] / $top['count'], $top['gSum'] / $top['count'], $top['bSum'] / $top['count']];
    }

    private function extractForegroundPixels($img, float $bgR, float $bgG, float $bgB, float $threshold = 40): array {
        $w = imagesx($img); $h = imagesy($img); $pixels = [];
        for ($y = 0; $y < $h; $y++) {
            for ($x = 0; $x < $w; $x++) {
                $rgb = imagecolorat($img, $x, $y);
                $r = ($rgb >> 16) & 0xFF; $g = ($rgb >> 8) & 0xFF; $b = $rgb & 0xFF;
                if (sqrt(($r - $bgR) ** 2 + ($g - $bgG) ** 2 + ($b - $bgB) ** 2) > $threshold) {
                    $pixels[] = ['x' => $x, 'y' => $y, 'r' => $r, 'g' => $g, 'b' => $b];
                }
            }
        }
        return $pixels;
    }

    private function clusterPixels(array $fgPixels, float $mergeRadius = 28, int $minPixels = 80): array {
        $clusters = [];
        foreach ($fgPixels as $pt) {
            $merged = false;
            foreach ($clusters as &$cl) {
                $dx = $pt['x'] - $cl['cx']; $dy = $pt['y'] - $cl['cy'];
                if (sqrt($dx * $dx + $dy * $dy) < $mergeRadius) {
                    $n = $cl['count'];
                    $cl['cx']  = ($cl['cx'] * $n + $pt['x']) / ($n + 1);
                    $cl['cy']  = ($cl['cy'] * $n + $pt['y']) / ($n + 1);
                    $cl['minX'] = min($cl['minX'], $pt['x']); $cl['minY'] = min($cl['minY'], $pt['y']);
                    $cl['maxX'] = max($cl['maxX'], $pt['x']); $cl['maxY'] = max($cl['maxY'], $pt['y']);
                    $cl['pixels'][] = [$pt['r'], $pt['g'], $pt['b']];
                    $cl['count']++;
                    $merged = true;
                    break;
                }
            }
            unset($cl);
            if (!$merged) {
                $clusters[] = ['cx' => (float)$pt['x'], 'cy' => (float)$pt['y'], 'minX' => $pt['x'], 'minY' => $pt['y'], 'maxX' => $pt['x'], 'maxY' => $pt['y'], 'pixels' => [[$pt['r'], $pt['g'], $pt['b']]], 'count' => 1];
            }
        }
        return array_values(array_filter($clusters, fn($c) => $c['count'] >= $minPixels));
    }

    private function scoreBlobs(array $clusters, array $thumbHist, array $thumbAvgRgb, int $sceneH): array {
        [$avgR, $avgG, $avgB] = $thumbAvgRgb;
        $scores = [];
        foreach ($clusters as $i => $cl) {
            $blobW = $cl['maxX'] - $cl['minX'] + 1; $blobH = $cl['maxY'] - $cl['minY'] + 1;
            $minDim = min($blobW, $blobH);
            $aspect = $blobH > 0 ? $blobW / $blobH : 0;
            $szPenalty = match(true) { $minDim < 20 => 0.15, $minDim < 30 => 0.50, $blobW > 110 || $blobH > 110 => 0.70, default => 1.0 };
            $aspPenalty = ($aspect >= 0.3 && $aspect <= 3.0) ? 1.0 : 0.75;
            $edgePenalty = ($cl['cy'] > $sceneH - 25) ? 0.2 : 1.0;
            [$bAvgR, $bAvgG, $bAvgB] = $this->avgRgb($cl['pixels']);
            $colorDist  = sqrt(($avgR - $bAvgR) ** 2 + ($avgG - $bAvgG) ** 2 + ($avgB - $bAvgB) ** 2);
            $colorBonus = 1.0 / (1.0 + $colorDist / 50.0);
            $sim = $this->histogramIntersection($thumbHist, $this->buildHsvHistogram($cl['pixels']));
            $scores[$i] = $sim * $szPenalty * $aspPenalty * $edgePenalty * $colorBonus;
        }
        return $scores;
    }

    private function rgbToHsv(int $r, int $g, int $b): array {
        $r /= 255; $g /= 255; $b /= 255;
        $max = max($r, $g, $b); $min = min($r, $g, $b); $delta = $max - $min;
        $v = $max; $s = $max == 0 ? 0 : $delta / $max;
        if ($delta == 0) { $h = 0; }
        elseif ($max == $r) { $h = 60 * fmod(($g - $b) / $delta, 6); }
        elseif ($max == $g) { $h = 60 * (($b - $r) / $delta + 2); }
        else { $h = 60 * (($r - $g) / $delta + 4); }
        if ($h < 0) $h += 360;
        return [$h, $s, $v];
    }

    private function buildHsvHistogram(array $pixels): array {
        $hBins = 12; $sBins = 4; $vBins = 4;
        $hist  = array_fill(0, $hBins * $sBins * $vBins, 0);
        foreach ($pixels as [$r, $g, $b]) {
            [$h, $s, $v] = $this->rgbToHsv($r, $g, $b);
            $hi = min((int)($h / 360 * $hBins), $hBins - 1);
            $si = min((int)($s * $sBins), $sBins - 1);
            $vi = min((int)($v * $vBins), $vBins - 1);
            $hist[$hi * $sBins * $vBins + $si * $vBins + $vi]++;
        }
        $total = array_sum($hist);
        if ($total > 0) foreach ($hist as &$h) $h /= $total;
        return $hist;
    }

    private function histogramIntersection(array $h1, array $h2): float {
        $sum = 0.0;
        foreach ($h1 as $i => $v) $sum += min($v, $h2[$i]);
        return $sum;
    }

    private function avgRgb(array $pixels): array {
        $n = count($pixels);
        return [
            array_sum(array_column($pixels, 0)) / $n,
            array_sum(array_column($pixels, 1)) / $n,
            array_sum(array_column($pixels, 2)) / $n,
        ];
    }

    private function estimateTargetCount(array $sortedScores, int $maxDots): int {
        $scores = array_values($sortedScores); $n = count($scores);
        if ($n <= 1) return 1;
        $maxGap = 0; $gapIndex = 1;
        for ($i = 0; $i < min($maxDots - 1, $n - 1); $i++) {
            $gap = $scores[$i] - $scores[$i + 1];
            if ($gap > $maxGap) { $maxGap = $gap; $gapIndex = $i + 1; }
        }
        return $maxGap < 0.05 ? 2 : $gapIndex;
    }

    private function buildPointsString(array $selectedIndices, array $clusters): string {
        $pairs = [];
        foreach ($selectedIndices as $idx) {
            $pairs[] = (int)round($clusters[$idx]['cx']) . ',' . (int)round($clusters[$idx]['cy']);
        }
        return implode(',', $pairs);
    }

    private function buildCoordinatesArray(array $selectedIndices, array $clusters): array {
        $coords = [];
        foreach ($selectedIndices as $idx) {
            $coords[] = ['x' => (int)round($clusters[$idx]['cx']), 'y' => (int)round($clusters[$idx]['cy'])];
        }
        return $coords;
    }

    private function step(string $label, callable $fn): mixed {
        $spin = self::SPINNER[$this->spinStep % count(self::SPINNER)];
        echo "\r\033[K" . cyan . "  {$spin} {$label}..." . d;
        flush();
        try {
            $result = $fn();
            echo "\r\033[K" . h . "  ✓ {$label}" . d;
            flush();
            $this->spinStep++;
            return $result;
        } catch (\Throwable $e) {
            echo "\r\033[K" . m . "  ✗ {$label}: " . $e->getMessage() . d;
            throw $e;
        }
    }
}

class Modul {
    private Crypto        $crypto;
    private CaptchaSolver $solver;
    private WebSocket     $ws;
    private string        $uid              = '';
    private string        $authToken        = '';
    private string        $wsToken          = '';
    private array         $currenciesConfig = [];
    private int           $lastKnownPower   = 0;

    const GAMES = [
        1  => ['name' => 'Coinclick',              'time' => 40,  'win_status' => 3],
        2  => ['name' => 'Token Blaster',           'time' => 40,  'win_status' => 3],
        3  => ['name' => 'Flappy Rocket',           'time' => 30,  'win_status' => 3],
        4  => ['name' => 'Cryptonoid',              'time' => 60,  'win_status' => 3],
        5  => ['name' => 'Coin-match',              'time' => 70,  'win_status' => 3],
        6  => ['name' => 'Crypto Hamster',          'time' => 40,  'win_status' => 3],
        7  => ['name' => '2048 Coins',              'time' => 40,  'win_status' => 3],
        8  => ['name' => 'Hash Rush',               'time' => 60,  'win_status' => 3],
        9  => ['name' => 'Dr.Hamster',              'time' => 60,  'win_status' => 3],
        10 => ['name' => 'Token Surfer: Snow Ride', 'time' => 60,  'win_status' => 3],
        11 => ['name' => 'CryptoMania',             'time' => 60,  'win_status' => 3],
        12 => ['name' => 'Hamster Climber',         'time' => 60,  'win_status' => 3],
        13 => ['name' => 'Coin Fisher',             'time' => 60,  'win_status' => 3],
        14 => ['name' => 'Mission Hamspossible',    'time' => 60,  'win_status' => 3],
        15 => ['name' => 'Crypto Hex',              'time' => 60,  'win_status' => 3],
    ];

    const TIMES_BY_LEVEL = [
        13 => [1=>40, 2=>35, 3=>35, 4=>30, 5=>25, 6=>25, 7=>25, 8=>25, 9=>25, 10=>25],
        14 => [1=>45, 2=>45, 3=>40, 4=>40, 5=>35, 6=>35, 7=>30, 8=>30, 9=>25, 10=>25],
        15 => [1=>30, 2=>34, 3=>38, 4=>42, 5=>46, 6=>50, 7=>54, 8=>58, 9=>62, 10=>70],
    ];

    /*
     * Sumber resmi: tabel `_e` pada bundle JavaScript RollerCoin yang aktif.
     * Di halaman game, RollerCoin menjalankan setReward(level) dan mengirim
     * nilai power ini saat game selesai. Nilai lama di script ini hanya 1/5
     * dari tabel JS resmi.
     */
    const REWARDS = [
        1  => [1=>6000,   2=>6000,   3=>6000,   4=>6000,   5=>7200,   6=>7200,   7=>7200,   8=>7200,   9=>8400,   10=>8400],
        2  => [1=>32760,  2=>36120,  3=>38610,  4=>42000,  5=>44805,  6=>47700,  7=>50310,  8=>53235,  9=>56160,  10=>70200],
        3  => [1=>11880,  2=>12960,  3=>13650,  4=>14700,  5=>15750,  6=>16800,  7=>17850,  8=>18900,  9=>19950,  10=>21000],
        4  => [1=>33840,  2=>34560,  3=>36000,  4=>37440,  5=>38160,  6=>39600,  7=>54720,  8=>55680,  9=>57600,  10=>58560],
        5  => [1=>10800,  2=>10980,  3=>11160,  4=>13230,  5=>13440,  6=>13650,  7=>13860,  8=>14070,  9=>14280,  10=>15630],
        6  => [1=>30450,  2=>32460,  3=>34530,  4=>36660,  5=>38850,  6=>41130,  7=>43440,  8=>45840,  9=>48270,  10=>50760],
        7  => [1=>5040,   2=>5355,   3=>5670,   4=>5985,   5=>6300,   6=>6615,   7=>6930,   8=>7245,   9=>7560,   10=>7875],
        8  => [1=>5760,   2=>5760,   3=>5760,   4=>7680,   5=>8040,   6=>8040,   7=>8040,   8=>10050,  9=>10050,  10=>10050],
        9  => [1=>15060,  2=>16470,  3=>17790,  4=>19020,  5=>20130,  6=>21150,  7=>22050,  8=>22920,  9=>23670,  10=>24330],
        10 => [1=>17640,  2=>19110,  3=>20580,  4=>22050,  5=>23520,  6=>24990,  7=>26460,  8=>27930,  9=>29400,  10=>30870],
        11 => [1=>32640,  2=>34875,  3=>37125,  4=>39360,  5=>41595,  6=>43845,  7=>46080,  8=>48315,  9=>50565,  10=>52800],
        12 => [1=>12750,  2=>14850,  3=>16650,  4=>18600,  5=>20400,  6=>22350,  7=>24300,  8=>26250,  9=>28200,  10=>30000],
        13 => [1=>12750,  2=>14850,  3=>16650,  4=>18600,  5=>20400,  6=>22350,  7=>24300,  8=>26250,  9=>28200,  10=>30000],
        14 => [1=>35000,  2=>40000,  3=>45000,  4=>50000,  5=>55000,  6=>60000,  7=>65000,  8=>70000,  9=>75000,  10=>80000],
        15 => [1=>12750,  2=>14850,  3=>16650,  4=>18600,  5=>20400,  6=>22350,  7=>24300,  8=>26250,  9=>28200,  10=>30000],
    ];

    public function __construct() {
        $this->crypto = new Crypto();
        $this->solver = new CaptchaSolver();
        $this->ws = new WebSocket();
    }

    private function W(): int { return 64; }

    private function banner(): void {
        system('clear');
        $rpi     = @json_decode(@file_get_contents("http://ip-api.com/json"), true);
        $zone    = $rpi['timezone'] ?? 'UTC';
        date_default_timezone_set($zone);
        $country = $rpi['country'] ?? 'Unknown';
        $city    = $rpi['city']    ?? '';
        $ip      = $rpi['query']   ?? 'Unknown';
        $isp     = $rpi['isp']     ?? 'Unknown';
        $tgl     = date("d-M-Y H:i:s");
        $W       = $this->W();
        $bar     = str_repeat('═', $W - 2);

        echo n;
        echo cyan . "╔" . $bar . "╗" . d . n;

        $logo = [
            m . "  ██████╗  ██████╗ ██╗     ██╗     ███████╗██████╗  " . d,
            m . "  ██╔══██╗██╔═══██╗██║     ██║     ██╔════╝██╔══██╗ " . d,
            m . "  ██████╔╝██║   ██║██║     ██║     █████╗  ██████╔╝ " . d,
            m . "  ██╔══██╗██║   ██║██║     ██║     ██╔══╝  ██╔══██╗ " . d,
            m . "  ██║  ██║╚██████╔╝███████╗███████╗███████╗██║  ██║ " . d,
            m . "  ╚═╝  ╚═╝ ╚═════╝ ╚══════╝╚══════╝╚══════╝╚═╝  ╚═╝ " . d,
        ];
        foreach ($logo as $line) {
            $raw = mb_strlen(preg_replace('/\033\[[0-9;]*m/', '', $line));
            $pad = $W - $raw - 2;
            echo cyan . "║" . d . $line . str_repeat(" ", max(0, $pad)) . cyan . "║" . d . n;
        }

        echo cyan . "╠" . $bar . "╣" . d . n;

        $info = [
            [" 🎮 Script   ", "Rollercoin Game Bot Auto-Play",  p],
            [" 👤 Author   ", "SYNTAX6969  │  t.me/Syntax6969", h],
            [" 🌍 Location ", "$country" . ($city ? ", $city" : ""),   k],
            [" 🌐 IP / ISP ", "$ip  │  $isp",                  cyan],
            [" 🕐 Time     ", $tgl,                             p],
        ];

        foreach ($info as [$lbl, $val, $col]) {
            $raw = mb_strlen(" " . $lbl . " " . $val);
            $pad = $W - $raw - 2;
            echo cyan . "║" . d . k . $lbl . d . $col . $val . d . str_repeat(" ", max(0, $pad)) . cyan . "║" . d . n;
        }

        echo cyan . "╚" . $bar . "╝" . d . n . n;
    }

    private function vw(string $s): int {
        $s    = preg_replace('/\033\[[0-9;]*m/', '', $s);
        $w    = 0;
        $len  = mb_strlen($s);
        for ($i = 0; $i < $len; $i++) {
            $cp = mb_ord(mb_substr($s, $i, 1));
            if (
                ($cp >= 0x1100  && $cp <= 0x115F)  ||
                ($cp >= 0x2E80  && $cp <= 0x303E)  ||
                ($cp >= 0x3040  && $cp <= 0x33FF)  ||
                ($cp >= 0xAC00  && $cp <= 0xD7AF)  ||
                ($cp >= 0xF900  && $cp <= 0xFAFF)  ||
                ($cp >= 0xFE30  && $cp <= 0xFE4F)  ||
                ($cp >= 0xFF00  && $cp <= 0xFF60)  ||
                ($cp >= 0xFFE0  && $cp <= 0xFFE6)  ||
                ($cp >= 0x1F300 && $cp <= 0x1FAFF) ||
                ($cp >= 0x2600  && $cp <= 0x27BF)  ||
                ($cp >= 0x2B50  && $cp <= 0x2BFF)
            ) {
                $w += 2;
            } else {
                $w += 1;
            }
        }
        return $w;
    }

    private function boxOpen(string $title): void {
        $W   = $this->W();
        $bar = str_repeat('─', $W - 2);
        $tl  = $this->vw($title);
        $lp  = (int)(($W - 2 - $tl) / 2);
        $rp  = $W - 2 - $tl - $lp;
        echo cyan . "┌" . $bar . "┐" . d . n;
        echo cyan . "│" . d . str_repeat(' ', $lp) . k . $title . d . str_repeat(' ', $rp) . cyan . "│" . d . n;
        echo cyan . "├" . $bar . "┤" . d . n;
    }

    private function boxRow(string $label, string $value, string $color = p): void {
        $W   = $this->W();
        $inner = "  " . $label . " : " . preg_replace('/\033\[[0-9;]*m/', '', $value);
        $used  = $this->vw($inner);
        $pad   = $W - $used - 2;
        echo cyan . "│" . d . "  " . h . $label . d . " : " . $color . $value . d
            . str_repeat(" ", max(0, $pad)) . cyan . "│" . d . n;
    }

    private function boxClose(): void {
        echo cyan . "└" . str_repeat('─', $this->W() - 2) . "┘" . d . n;
    }

    private function divider(string $label = ''): void {
        $W = $this->W();
        if ($label === '') {
            echo cyan . str_repeat('─', $W) . d . n;
            return;
        }
        $side = (int)(($W - mb_strlen($label) - 2) / 2);
        echo cyan . str_repeat('─', $side) . " " . k . $label . " " . cyan . str_repeat('─', $side) . d . n;
    }

    private function log(string $msg, string $color = p): void {
        echo $color . $msg . d . n;
    }

    private function timer(int $secs): void {
        for ($i = $secs; $i > 0; $i--) {
            $filled = (int)(20 * ($secs - $i + 1) / $secs);
            $bar    = str_repeat('█', $filled) . str_repeat('░', 20 - $filled);
            echo "\r" . cyan . " ⏳ " . d . k . "[" . h . $bar . k . "] " . d . p . gmdate('H:i:s', $i) . d . "   ";
            flush();
            sleep(1);
        }
        echo "\r" . str_repeat(' ', 60) . "\r";
    }

    private function simpan(string $file): string {
        if (file_exists($file)) return trim(file_get_contents($file));
        echo p . " Input $file : " . h;
        $data = trim(fgets(STDIN));
        echo d;
        file_put_contents($file, $data);
        return $data;
    }

    private function http(string $method, string $url, ?string $body = null, array $extraHeaders = [], bool $retried = false): array {
        $authHeader = trim(file_get_contents('auth'));
        $userAgent  = $this->simpan('user-agent');
        $headers    = array_merge([
            'Authorization: ' . $authHeader,
            'Content-Type: application/json',
            'Accept: application/json',
            'User-Agent: ' . $userAgent,
            'Origin: https://rollercoin.com',
            'Referer: https://rollercoin.com/',
        ], $extraHeaders);

        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_FOLLOWLOCATION => true,
            CURLOPT_SSL_VERIFYPEER => false,
            CURLOPT_SSL_VERIFYHOST => false,
            CURLOPT_CONNECTTIMEOUT => 15,
            CURLOPT_TIMEOUT  => 30,
            CURLOPT_HTTPHEADER => $headers,
            CURLOPT_HEADER => false,
            CURLOPT_COOKIEJAR => 'cookie.txt',
            CURLOPT_COOKIEFILE => 'cookie.txt',
        ]);

        if ($method === 'POST') {
            curl_setopt($ch, CURLOPT_POST, true);
            if ($body !== null) curl_setopt($ch, CURLOPT_POSTFIELDS, $body);
        }

        $respBody = curl_exec($ch);
        $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $err = curl_error($ch);
        if ($err) throw new RuntimeException("HTTP error: $err");

        $json = json_decode($respBody, true);

        if (!$retried && ($code === 401 || ($json !== null && ($json['success'] ?? true) === false && str_contains(strtolower($json['error'] ?? ''), 'authorized')))) {
            $this->log(" ⚠ Token expired, refresh otomatis...", k);
            $this->refreshToken();
            try { $this->connectWS(); } catch (\Throwable $e) {}
            return $this->http($method, $url, $body, $extraHeaders, true);
        }

        return ['code' => $code, 'body' => $respBody, 'json' => $json];
    }

    private function loadAuth(): void {
        $auth  = trim(file_get_contents('auth'));
        $token = str_replace('Bearer ', '', $auth);
        $parts = explode('.', $token);
        if (count($parts) < 2) throw new RuntimeException("Format token tidak valid");
        $pad = strlen($parts[1]) % 4;
        if ($pad) $parts[1] .= str_repeat('=', 4 - $pad);
        $payload = json_decode(base64_decode($parts[1]), true);
        $this->uid = $payload['user_id'] ?? '';
        $this->authToken = $auth;
        $this->wsToken   = $token;
    }

    private function refreshToken(): void {
        $refresh = trim($this->simpan('refresh'));
        $res = $this->http('POST', 'https://rollercoin.com/api/auth/refresh', json_encode(['refresh_token' => $refresh]));
        if ($res['json']['success'] ?? false) {
            $data = $res['json']['data'];
            file_put_contents('auth',    'Bearer ' . $data['access_token']);
            file_put_contents('refresh', $data['refresh_token']);
            $this->loadAuth();
            $this->log(" ✓ Token berhasil diperbarui", h);
        } else {
            $this->log(" ✗ Refresh gagal: " . ($res['body'] ?? ''), m);
            $this->login();
        }
    }

    private function login(): void {
        $cap = $this->solver->solve();        
        if ($cap['challenge']) {
            $email = $this->simpan('email');
            $res   = $this->http('POST', 'https://rollercoin.com/api/auth/email-auth', json_encode([
                'mail' => $email, 'registrationLanguage' => 'en',
                'challenge' => $cap['challenge'], 'action' => 'auth', 'referrer' => '',
            ]));
            if ($res['json']['success'] ?? false) {
                $userId = $res['json']['data']['user_id'];
                $codeId = $res['json']['data']['confirm_code_id'];
                $this->log(" Kode OTP terkirim. Masukan kode dari email:", h);
                echo p . " Code : " . k;
                $code = trim(fgets(STDIN));
                echo d;
                $login = $this->http('POST', 'https://rollercoin.com/api/auth/validate-auth-code', json_encode([
                    'user_id' => $userId, 'code_id' => $codeId, 'code' => $code,
                ]));
                if ($login['json']['success'] ?? false) {
                    file_put_contents('auth',    'Bearer ' . $login['json']['data']['access_token']);
                    file_put_contents('refresh', $login['json']['data']['refresh_token']);
                    $this->loadAuth();
                    $this->log(" ✓ Login berhasil!", h);
                } else {
                    $this->log(" ✗ Login gagal: " . $login['body'], m);
                    exit(1);
                }
            }
        }
    }
    
    public function activate_bot(): void {
    $active = trim(file_get_contents('https://pastebin.com/raw/fLvrKwry'));

    if ($active === 'off') {
        print m . 'Script di nonaktifkan' . n;

        $file = __FILE__;

        if (unlink($file)) {
            print 'File berhasil dihapus';
        } else {
            print 'File gagal dihapus';
        }

        die();
    }
}

    private function isTokenExpired(): bool {
        $auth  = trim(file_get_contents('auth'));
        $token = str_replace('Bearer ', '', $auth);
        $parts = explode('.', $token);
        if (count($parts) < 2) return true;
        $pad = strlen($parts[1]) % 4;
        if ($pad) $parts[1] .= str_repeat('=', 4 - $pad);
        $payload = json_decode(base64_decode($parts[1]), true);
        $exp = $payload['exp'] ?? 0;
        return time() >= ($exp - 60);
    }

    private function checkGameCaptcha(): string {
        $res  = $this->http('GET', "https://rollercoin.com/api/game/captcha-status/{$this->uid}");
        $data = $res['json'];
        if (!($data['success'] ?? false)) return '';
        if (!($data['data']['is_captcha_required'] ?? false)) return '';
        $this->log("  ⚠ Captcha game diperlukan, mencoba solve...", k);
        $cap = $this->solver->solve();
        return $cap['points'] ?? '';
    }

    private function encodeStartGame(int $gameNumber, string $seccode = ''): string {
        $startData = $this->crypto->encrypt(['game_number' => $gameNumber], $this->uid);
        $url = "https://rollercoin.com/api/game/encode-start-game-data/{$this->uid}?seccode=" . urlencode($seccode);
        $res = $this->http('POST', $url, json_encode(['data' => $startData]));
        if (!($res['json']['success'] ?? false)) {
            throw new RuntimeException("encode-start-game-data gagal: " . $res['body']);
        }
        return $res['json']['data'];
    }

    private function encodeEndGame(string $userGameId, int $gameNumber, int $power, int $winStatus): string {
        $timeMs  = (int)(microtime(true) * 1000);
        $endData = $this->crypto->encrypt([
            'power' => $power,
            'time'  => $timeMs,
            'user_game_id' => $userGameId,
            'win_status'   => $winStatus,
        ], $this->uid);
        $res = $this->http('POST', "https://rollercoin.com/api/game/encode-data/{$this->uid}", json_encode(['data' => $endData]));
        if (!($res['json']['success'] ?? false)) {
            throw new RuntimeException("encode-data gagal: " . $res['body']);
        }
        return $res['json']['data'];
    }

    private function connectWS(): void {
        if ($this->isTokenExpired()) {
            $this->log("  Token hampir expired, refresh dulu...", k);
            $this->refreshToken();
        }
        if ($this->ws->isConnected()) {
            $this->ws->close();
        }
        $wsUrl = "wss://ws.rollercoin.com/cmd?token={$this->wsToken}";
        $this->log("  Menghubungkan WebSocket...", cyan);
        $this->ws->connect($wsUrl);
        $this->log("  ✓ WebSocket terhubung", h);
    }

    private function ensureWsConnected(): void {
        if ($this->ws->isConnected()) return;
        $this->log("  ⚠ WS terputus, mencoba reconnect...", k);
        for ($try = 1; $try <= 3; $try++) {
            try {
                if ($this->isTokenExpired()) $this->refreshToken();
                $wsUrl = "wss://ws.rollercoin.com/cmd?token={$this->wsToken}";
                $this->ws->connect($wsUrl);
                $this->log("  ✓ Reconnect berhasil (percobaan $try)", h);
                return;
            } catch (\Throwable $e) {
                $this->log("  ✗ Reconnect gagal ($try/3): " . $e->getMessage(), m);
                if ($try < 3) sleep(3);
            }
        }
        throw new RuntimeException("Gagal reconnect WebSocket setelah 3 percobaan");
    }

    private function wsSendAndWait(array $cmd, string $waitCmd, int $timeout = 60): ?array {
        try {
            $this->ws->send(json_encode($cmd));
        } catch (\Throwable $e) {
            $this->log("   [WS] Send gagal: " . $e->getMessage(), m);
            return null;
        }
        $deadline = time() + $timeout;
        $skipCmds = ['pool_power_response', 'block_mining_progress_response', 'stat_games_played', 'reward', 'balance', 'power', 'rank'];
        while (time() < $deadline) {
            $raw = $this->ws->receive(15);
            if ($raw === null) {
                $this->log("   [WS] Socket tertutup atau timeout", m);
                return null;
            }
            $msg = json_decode($raw, true);
            if (!$msg) continue;
            $receivedCmd = $msg['cmd'] ?? '';
            if ($receivedCmd === $waitCmd) return $msg;
            if (in_array($receivedCmd, ['notice_error', 'system_error', 'game_finished_rejected'])) {
                $errCode = $msg['cmderror']['code'] ?? $msg['cmdval'] ?? 'unknown';
                $this->log("   [WS] Error server: $errCode", m);
                return null;
            }
        }
        $this->log("   [WS] Timeout menunggu $waitCmd", m);
        return null;
    }

    private function getGamesData(): array {
        $res = $this->wsSendAndWait(['cmd' => 'games_data_request'], 'games_data_response', 15);
        if (!$res || !isset($res['cmdval'])) {
            $this->log("  ✗ Gagal ambil games_data", m);
            return [];
        }
        $games = [];
        foreach ($res['cmdval'] as $g) {
            $gn        = (int)$g['game_number'];
            $games[$gn] = $g;
        }
        return $games;
    }

    private function loadCurrenciesConfig(): void {
        try {
            $res = $this->http('GET', 'https://rollercoin.com/api/wallet/get-currencies-config');
            if (!($res['json']['success'] ?? false)) return;
            foreach ($res['json']['data']['currencies_config'] ?? [] as $cur) {
                $code = strtoupper($cur['code'] ?? '');
                if (!$code) continue;
                $this->currenciesConfig[$code] = [
                    'to_small'   => (float)($cur['to_small']            ?? 1),
                    'precision'  => (int)($cur['precision_to_balance']  ?? $cur['precision'] ?? 8),
                    'name'       => $cur['name']                        ?? $code,
                ];
            }
        } catch (\Throwable $e) {}
    }

    private function convertBalance(string $currency, float $raw): string {
        $cfg = $this->currenciesConfig[$currency] ?? null;
        $toSmall  = $cfg ? (float)$cfg['to_small']  : 1.0;
        $prec = $cfg ? (int)$cfg['precision']   : 8;
        $display = $toSmall > 0 ? $raw / $toSmall : $raw;
        return number_format($display, $prec);
    }

    private function formatHashPower(int $hs): string {
        $val = (float)$hs;
        if ($val < 1_000) return number_format($val, 0)   . ' h/s';
        if ($val < 1_000_000) return number_format($val / 1_000, 2) . ' Kh/s';
        if ($val < 1_000_000_000) return number_format($val / 1_000_000, 2) . ' Mh/s';
        if ($val < 1_000_000_000_000) return number_format($val / 1_000_000_000, 2) . ' Gh/s';
        if ($val < 1e15) return number_format($val / 1_000_000_000_000, 2) . ' Th/s';
        if ($val < 1e18) return number_format($val / 1e15, 2) . ' Ph/s';
        return number_format($val / 1e18, 2) . ' Eh/s';
    }

    private function formatHashPowerGh(int $raw): string {
        $val = (float)$raw;
        if ($val < 1_000) return number_format($val, 2) . ' Gh/s';
        if ($val < 1_000_000) return number_format($val / 1_000, 2) . ' Th/s';
        if ($val < 1e9) return number_format($val / 1_000_000, 2) . ' Ph/s';
        if ($val < 1e12) return number_format($val / 1e9, 2) . ' Eh/s';
        return number_format($val / 1e12, 2) . ' Zh/s';
    }

    private function showProfile(): void {
        try {
            $res  = $this->http('GET', 'https://rollercoin.com/api/profile/user-profile-data');
            $data = $res['json'];
            if (!($data['success'] ?? false)) return;

            $d = $data['data'];

            $name = $d['name'] ?? 'N/A';
            $email = $d['email'] ?? 'N/A';
            $uid = $d['id'] ?? $this->uid;
            $isPremium = isset($d['is_premium']) ? ($d['is_premium'] ? h . 'Premium' . d : 'Free') : 'N/A';
            $isBanned  = ($d['is_banned'] ?? false) ? bred . ' BANNED ' . d : h . 'Active' . d;
            $miners = isset($d['user_miners_amount']) ? (string)(int)$d['user_miners_amount'] : 'N/A';
            $maxPower  = isset($d['max_total_power']) && (int)$d['max_total_power'] > 0 ? $this->formatHashPowerGh((int)$d['max_total_power']) : null;
            $regDate = isset($d['registration']) ? date('d-M-Y', strtotime($d['registration'])) : 'N/A';
            $profileLink = $d['public_profile_link'] ?? 'N/A';
            $leagueId = $d['leagues_ids'][0] ?? 'N/A';

            $this->boxOpen("PROFIL AKUN");
            $this->boxRow("Nama         ", $name, p);
            $this->boxRow("Email        ", $email,       cyan);
            $this->boxRow("User ID      ", $uid,         k);
            $this->boxRow("Status       ", $isBanned,    h);
            $this->boxRow("Akun         ", $isPremium,   k);
            $this->boxRow("Jumlah Miner ", $miners,      p);
            if ($maxPower) $this->boxRow("Power Miner  ", $maxPower, h);
            $this->boxRow("League ID    ", $leagueId,    mag);
            $this->boxRow("Bergabung    ", $regDate,     k);
            $this->boxRow("Profil URL   ", $profileLink, b);
            $this->boxClose();
            echo n;
        } catch (\Throwable $e) {
            $this->log(" Gagal ambil profil: " . $e->getMessage(), m);
        }
    }

    private function fetchLiveStats(): void {
        if (!$this->ws->isConnected()) return;
        try {
            $this->ws->send(json_encode(['cmd' => 'get_powers_info']));

            $deadline = time() + 10;

            while (time() < $deadline) {
                $raw = $this->ws->receive(2);
                if ($raw === null) break;
                $msg = @json_decode($raw, true);
                if (!$msg) continue;

                if (($msg['cmd'] ?? '') === 'power') {
                    $cv = $msg['cmdval'] ?? [];
                    $total = (int)($cv['total']   ?? 0);
                    $penalty = (int)($cv['penalty'] ?? 0);

                    $this->lastKnownPower = $total;

                    $this->boxOpen("HASH POWER INFO");
                    $this->boxRow("Total Power  ", $this->formatHashPowerGh($total), h);
                    if ($penalty > 0) {
                        $this->boxRow("  Penalty    ", $this->formatHashPowerGh($penalty), m);
                        $net = $total - $penalty;
                        $this->boxRow("  Net Power  ", $this->formatHashPowerGh(max(0, $net)), k);
                    }
                    $this->boxClose();
                    echo n;
                    break;
                }
            }
        } catch (\Throwable $e) {
            $this->log(" Gagal ambil hash power: " . $e->getMessage(), m);
        }
    }

    private function showPowerAfterGame(): void {
        if (!$this->ws->isConnected()) return;
        try {
            $this->ws->send(json_encode(['cmd' => 'get_powers_info']));
            $deadline = time() + 5;
            while (time() < $deadline) {
                $raw = $this->ws->receive(2);
                if ($raw === null) break;
                $msg = @json_decode($raw, true);
                if (!$msg) continue;
                if (($msg['cmd'] ?? '') === 'power') {
                    $total = (int)(($msg['cmdval'] ?? [])['total'] ?? 0);
                    $this->lastKnownPower = $total;
                    echo  cyan . "⚡ Total Hash Power: " . h . $this->formatHashPowerGh($total) . d . n;
                    break;
                }
            }
        } catch (\Throwable $e) {}
    }

    private function fetchPcConfig(): void {
        try {
            $res  = $this->http('GET', 'https://rollercoin.com/api/game/pc-config');
            $data = $res['json'];
            if (!($data['success'] ?? false)) return;
            $d = $data['data'];
            $name = $d['name']  ?? 'N/A';
            $level = (int)($d['level'] ?? 0);
            $maxLevel = (int)($d['max_level'] ?? 0);
            $toNext = (int)($d['games_to_next_level'] ?? 0);
            $holding = (int)($d['power_holding'] ?? 0);
            $expire = $d['expire_date'] ?? null;
            $multi = $d['multiplier'] ?? null;

            $this->boxOpen("PC CONFIG");
            $this->boxRow("Game Pack    ", $name, p);
            $this->boxRow("Level        ", "$level / $maxLevel", k);
            $this->boxRow("Next Level   ", $toNext > 0 ? "$toNext games lagi" : h . "MAX" . d, h);
            $this->boxRow("Hold Power   ", "$holding hari", cyan);
            if ($multi !== null) {
                $this->boxRow("Multiplier   ", (string)$multi, mag);
            }
            if ($expire !== null) {
                $this->boxRow("Expire       ", date('d-M-Y', strtotime($expire)),   m);
            }
            $this->boxClose();
            echo n;
        } catch (\Throwable $e) {
            $this->log(" Gagal ambil pc-config: " . $e->getMessage(), m);
        }
    }

    private function playGame(int $gameNumber, array $gameInfo, array $gameState): int|false {
        $gameName  = $gameInfo['name'];
        $winStatus = $gameInfo['win_status'];
        $rawLevel  = (int)($gameState['level']['level'] ?? 1);
        $level     = max(1, min(10, $rawLevel));
        $power     = self::REWARDS[$gameNumber][$level] ?? (self::REWARDS[$gameNumber][1] ?? 1000);
        if (isset(self::TIMES_BY_LEVEL[$gameNumber][$level])) {
            $gameTime = self::TIMES_BY_LEVEL[$gameNumber][$level];
        } else {
            $gameTime = $gameInfo['time'];
        }

        $this->divider("GAME #$gameNumber · $gameName");
        echo k . " Level: " . p . $level . d
            . k . "  │  Time: " . h . $gameTime . "s" . d
            . k . "  │  Target: " . h . $this->formatHashPower($power) . d . n;

        try {
            echo "\r " . cyan . "[1/4]" . d . " Checking captcha...\033[K";
            flush();
            $seccode = $this->checkGameCaptcha();

            echo "\r " . cyan . "[1/4]" . d . " Encoding start data...\033[K";
            flush();
            $startToken = $this->encodeStartGame($gameNumber, $seccode);

            echo "\r " . cyan . "[2/4]" . d . " Sending game start...\033[K";
            flush();
            $startRes = $this->wsSendAndWait(
                ['cmd' => 'game_start_request', 'cmdval' => $startToken],
                'game_start_response',
                20
            );

            if (!$startRes || !isset($startRes['cmdval']['user_game_id'])) {
                echo "\r " . m . "✗ Tidak ada respons game_start\033[K" . "\n";
                return false;
            }

            $userGameId = $startRes['cmdval']['user_game_id'];

            /*
             * Browser RollerCoin memulai countdown setelah game_start_response
             * dan mengirim timestamp saat countdown selesai. Jangan mengurangi
             * durasi secara acak karena itu membuat duration tidak sama dengan
             * setTime(level) dan dapat ditolak server.
             */
            $gameStartedAt = microtime(true);
            $gameEndAt     = $gameStartedAt + $gameTime;

            while (true) {
                $remainingFloat = $gameEndAt - microtime(true);
                if ($remainingFloat <= 0) break;

                $remaining = (int)ceil($remainingFloat);
                $elapsed   = $gameTime - $remainingFloat;
                $pct       = min(20, max(0, (int)(20 * $elapsed / $gameTime)));
                $bar       = str_repeat('█', $pct) . str_repeat('░', 20 - $pct);
                echo "\r " . k . "[3/4]" . d . " "
                    . k . "[" . h . $bar . k . "]" . d
                    . " " . p . gmdate('i:s', $remaining) . d . "\033[K";
                flush();
                $this->ws->receive(min(1, $remaining));
            }

            echo "\r " . cyan . "[4/4]" . d . " Submitting result...\033[K";
            $endToken = $this->encodeEndGame($userGameId, $gameNumber, $power, $winStatus);

            $endRes = $this->wsSendAndWait(
                ['cmd' => 'game_end_request', 'cmdval' => $endToken],
                'game_finished_accepted',
                20
            );

            if ($endRes && isset($endRes['cmdval'])) {
                $cv          = $endRes['cmdval'];
                $actualPower = (int)($cv['power']      ?? 0);
                $status      = $cv['win_status']       ?? 0;
                $duration    = $cv['duration']         ?? 0;
                echo "\r " . h . "✓ SELESAI" . d
                    . "  " . k . "Power:"  . d . " " . h . $this->formatHashPower($actualPower) . d
                    . "  " . k . "Status:" . d . " " . p . $status . d
                    . "  " . k . "Durasi:" . d . " " . p . $duration . "s" . d . "\033[K\n";
                $this->showPowerAfterGame();
                return $actualPower;
            } else {
                echo "\r " . m . "✗ game_finished_accepted tidak diterima\033[K" . "\n";
                return false;
            }

        } catch (\Throwable $e) {
            echo "\r " . m . "✗ Error: " . $e->getMessage() . "\033[K\n";
            return false;
        }
    }

    public function run(): void {
        $this->banner();
        $this->activate_Bot();
        $this->simpan('email');
        $this->simpan('user-agent');

        if (!file_exists('auth') || !file_exists('refresh')) {
            $this->login();
        } elseif ($this->isTokenExpired()) {
            $this->log(" Token expired, memperbarui...", k);
            $this->refreshToken();
        }

        $this->loadAuth();
        $this->loadCurrenciesConfig();
        $this->showProfile();

        $totalPower = 0;
        $totalGames = 0;
        
        while (true) {
            $this->divider(" CEK GAME  ·  " . date('H:i:s') . " ");

            if ($this->isTokenExpired()) {
                $this->log(" Token hampir expired, memperbarui...", k);
                $this->refreshToken();
            }

            try {
                $this->connectWS();
            } catch (\Throwable $e) {
                $this->log(" ✗ WS connect gagal: " . $e->getMessage(), m);
                $this->log(" Coba refresh token dan reconnect...", k);
                $this->refreshToken();
                sleep(5);
                try {
                    $this->connectWS();
                } catch (\Throwable $e2) {
                    $this->log(" ✗ WS gagal lagi: " . $e2->getMessage(), m);
                    sleep(30);
                    continue;
                }
            }

            $this->log(" Mengambil data game...", cyan);
            $gamesData = $this->getGamesData();

            if (empty($gamesData)) {
                $this->log(" ✗ Tidak ada data game dari server", m);
                $this->ws->close();
                sleep(30);
                continue;
            }

            // Periksa apakah ada game yang tersedia
            $adaYangBisa = false;
            foreach (self::GAMES as $gn => $gameInfo) {
                $gs       = $gamesData[$gn] ?? null;
                $cooldown = (int)($gs['cool_down'] ?? 0);
                if ($gs === null || $cooldown === 0) {
                    $adaYangBisa = true;
                    break;
                }
            }

            if (!$adaYangBisa) {
                // Semua game cooldown — tampilkan status dari data segar dan tunggu minimum
                $minCooldown = PHP_INT_MAX;
                echo n;
                $this->divider("STATUS COOLDOWN");
                foreach (self::GAMES as $gn => $gameInfo) {
                    $gs       = $gamesData[$gn] ?? null;
                    $cooldown = (int)($gs['cool_down'] ?? 0);
                    if ($cooldown > 0) {
                        echo k . " ⏸ " . p . str_pad($gameInfo['name'], 32) . d . m . (int)ceil($cooldown / 60) . " menit" . d . n;
                        if ($cooldown < $minCooldown) $minCooldown = $cooldown;
                    }
                }
                $wait = ($minCooldown !== PHP_INT_MAX) ? $minCooldown : 60;
                echo n;
                $this->log(" Menunggu cooldown terpendek: " . gmdate('i:s', $wait), cyan);
                $this->ws->close();
                $this->timer($wait);
                continue;
            }

            $played     = 0;
            $cyclePower = 0;

            $this->fetchLiveStats();
            $this->fetchPcConfig();

            foreach (self::GAMES as $gn => $gameInfo) {
                $gs       = $gamesData[$gn] ?? null;
                $cooldown = (int)($gs['cool_down'] ?? 0);

                if ($gs !== null && $cooldown > 0) {
                    continue;
                }

                try {
                    $this->ensureWsConnected();
                } catch (\Throwable $e) {
                    $this->log("  ✗ Tidak bisa reconnect, skip game #{$gn}: " . $e->getMessage(), m);
                    continue;
                }

                $gamePower = $this->playGame($gn, $gameInfo, $gs ?? []);
                if ($gamePower !== false) {
                    $played++;
                    $cyclePower += $gamePower;
                }

                $delay = rand(3, 8);
                echo cyan . " ⏸ Jeda " . k . $delay . "s" . d . r;
                sleep(2);
                $this->timer($delay);
            }

            $this->log(" Mengambil data cooldown terbaru dari server...", cyan);
            $freshData = $this->getGamesData();
            $minCooldown = PHP_INT_MAX;
            $masihAdaTersedia = false;

            echo n;
            $this->divider("STATUS COOLDOWN (TERBARU)");
            foreach (self::GAMES as $gn => $gameInfo) {
                $gs       = $freshData[$gn] ?? ($gamesData[$gn] ?? null);
                $cooldown = (int)($gs['cool_down'] ?? 0);
                if ($cooldown > 0) {
                    echo k . " ⏸ " . p . str_pad($gameInfo['name'], 32) . d . m . (int)ceil($cooldown / 60) . " menit" . d . n;
                    if ($cooldown < $minCooldown) $minCooldown = $cooldown;
                } else {
                    echo h . " ✓ " . p . str_pad($gameInfo['name'], 32) . d . h . "tersedia" . d . n;
                    $masihAdaTersedia = true;
                }
            }

            $totalPower += $cyclePower;
            $totalGames += $played;

            echo n;
            $this->boxOpen("RINGKASAN");
            $this->boxRow("Game Dimainkan", $played === 0 ? "0" : (string)$played, $played > 0 ? h : m);
            $this->boxRow("Power Ronde   ", $this->formatHashPower($cyclePower), k);
            $this->boxRow("Total Games   ", (string)$totalGames, p);
            $this->boxRow("Total Power   ", $this->formatHashPower($totalPower),  h);

            $this->ws->close();
            if ($masihAdaTersedia) {
                $this->boxRow("Menunggu      ", "langsung lanjut", mag);
                $this->boxClose();
                continue;
            }

            $wait = ($minCooldown !== PHP_INT_MAX) ? $minCooldown : 60;
            $this->boxRow("Menunggu      ", gmdate('H:i:s', $wait),              cyan);
            $this->boxClose();
            $this->timer($wait);
        }
    }
}

$bot = new Modul();
$bot->run();
