<?php
declare(strict_types=1);

const IMAGE_TYPES = [
    'image/jpeg' => 'jpg',
    'image/png' => 'png',
    'image/webp' => 'webp',
    'image/gif' => 'gif',
];
const VIDEO_TYPES = [
    'video/mp4' => 'mp4',
    'video/webm' => 'webm',
    'video/quicktime' => 'mov',
];

function upload_dir(): string
{
    $dir = config('upload_dir');
    if (!is_dir($dir)) {
        mkdir($dir, 0775, true);
    }
    return $dir;
}

/**
 * Normalises $_FILES[$field] (single or multiple) into a list of files.
 */
function uploaded_files(string $field): array
{
    if (empty($_FILES[$field])) {
        return [];
    }
    $f = $_FILES[$field];
    if (!is_array($f['name'])) {
        return $f['error'] === UPLOAD_ERR_NO_FILE ? [] : [$f];
    }
    $out = [];
    foreach ($f['name'] as $i => $name) {
        if ($f['error'][$i] === UPLOAD_ERR_NO_FILE) {
            continue;
        }
        $out[] = ['name' => $name, 'tmp_name' => $f['tmp_name'][$i], 'error' => $f['error'][$i], 'size' => $f['size'][$i]];
    }
    return $out;
}

/**
 * Validates and stores an uploaded file outside the web root.
 * The MIME type is detected from file contents (never trusted from the browser)
 * and the stored name is random, so uploads can never be executed.
 *
 * @return array{filename: string, mime: string}
 */
function store_upload(array $file, bool $allowVideo): array
{
    if ($file['error'] !== UPLOAD_ERR_OK) {
        $msg = in_array($file['error'], [UPLOAD_ERR_INI_SIZE, UPLOAD_ERR_FORM_SIZE], true)
            ? 'Fayl çox böyükdür.' : 'Fayl yüklənmədi.';
        throw new HttpError(400, $msg);
    }
    if ($file['size'] > config('max_upload_mb', 200) * 1024 * 1024) {
        throw new HttpError(400, 'Fayl çox böyükdür.');
    }
    if (!is_uploaded_file($file['tmp_name']) && PHP_SAPI !== 'cli') {
        throw new HttpError(400, 'Yanlış fayl.');
    }
    $mime = (new finfo(FILEINFO_MIME_TYPE))->file($file['tmp_name']) ?: '';
    $allowed = $allowVideo ? IMAGE_TYPES + VIDEO_TYPES : IMAGE_TYPES;
    if (!isset($allowed[$mime])) {
        throw new HttpError(400, 'Dəstəklənməyən fayl tipi: ' . $mime);
    }
    if (isset(IMAGE_TYPES[$mime]) && @getimagesize($file['tmp_name']) === false) {
        throw new HttpError(400, 'Şəkil faylı zədəlidir.');
    }
    $filename = bin2hex(random_bytes(16)) . '.' . $allowed[$mime];
    $dest = upload_dir() . '/' . $filename;
    $moved = PHP_SAPI === 'cli' ? copy($file['tmp_name'], $dest) : move_uploaded_file($file['tmp_name'], $dest);
    if (!$moved) {
        throw new HttpError(500, 'Fayl saxlanmadı.');
    }
    return ['filename' => $filename, 'mime' => $mime];
}

function delete_upload(?string $filename): void
{
    if ($filename && preg_match('/^[a-f0-9]{32}\.\w{3,4}$/', $filename)) {
        @unlink(upload_dir() . '/' . $filename);
    }
}

/**
 * Streams a stored file with HTTP Range support (needed for video seeking).
 */
function stream_file(string $filename, string $mime, string $cache): never
{
    $path = upload_dir() . '/' . basename($filename);
    if (!is_file($path)) {
        http_response_code(404);
        exit;
    }
    $size = filesize($path);
    $start = 0;
    $end = $size - 1;

    header('Content-Type: ' . $mime);
    header('Accept-Ranges: bytes');
    header('Cache-Control: ' . $cache);
    header('X-Content-Type-Options: nosniff');
    header('Content-Disposition: inline');

    if (isset($_SERVER['HTTP_RANGE']) && preg_match('/bytes=(\d*)-(\d*)/', $_SERVER['HTTP_RANGE'], $m)) {
        if ($m[1] === '' && $m[2] !== '') {
            $start = max(0, $size - (int) $m[2]);
        } else {
            $start = (int) $m[1];
            if ($m[2] !== '') {
                $end = min((int) $m[2], $size - 1);
            }
        }
        if ($start > $end || $start >= $size) {
            http_response_code(416);
            header("Content-Range: bytes */$size");
            exit;
        }
        http_response_code(206);
        header("Content-Range: bytes $start-$end/$size");
    }

    $length = $end - $start + 1;
    header('Content-Length: ' . $length);
    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'HEAD') {
        exit;
    }
    $fp = fopen($path, 'rb');
    fseek($fp, $start);
    $left = $length;
    while ($left > 0 && !feof($fp) && !connection_aborted()) {
        $chunk = fread($fp, min(8192 * 8, $left));
        echo $chunk;
        $left -= strlen($chunk);
        flush();
    }
    fclose($fp);
    exit;
}
