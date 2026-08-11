param(
    [Parameter(Mandatory = $true)]
    [string]$DumpPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'

$resolvedDump = (Resolve-Path -LiteralPath $DumpPath).Path
$insertLines = Get-Content -LiteralPath $resolvedDump -Encoding UTF8 |
    Where-Object { $_ -like 'INSERT INTO *' }

if ($insertLines.Count -eq 0) {
    throw "The dump contains no INSERT statements: $resolvedDump"
}

$generated = [System.Collections.Generic.List[string]]::new()
$generated.Add('-- Film-service catalog snapshot generated from the live development database on 2026-08-10.')
$generated.Add('-- Runtime outbox rows are intentionally excluded: replaying historical integration events is not seed data.')
$generated.Add('-- The marker prevents spring.sql.init.mode=always from resurrecting episodes deleted after first initialization.')
$generated.Add('-- All episode titles are Vietnamese with diacritics. Episode video_file_id stays NULL')
$generated.Add('-- until the confirmed Vagabond source files are uploaded and their real file IDs are provided.')
$generated.Add('')
$generated.Add('SET NAMES utf8mb4;')
$generated.Add('SET FOREIGN_KEY_CHECKS = 0;')
$generated.Add('CREATE TABLE IF NOT EXISTS `film_seed_metadata` (`seed_key` varchar(100) NOT NULL, `applied_at` datetime(6) NOT NULL, PRIMARY KEY (`seed_key`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;')
$generated.Add('SET @film_seed_required = NOT EXISTS (SELECT 1 FROM `film_seed_metadata` WHERE `seed_key` = ''catalog-2026-08-10'');')
$generated.Add('')

$matched = 0
foreach ($line in $insertLines) {
    if ($line -notmatch '^INSERT INTO `([^`]+)` \((.+)\) VALUES \((.*)\);$') {
        throw "Unsupported INSERT format in dump: $line"
    }

    $table = $Matches[1]
    $columns = $Matches[2]
    $values = $Matches[3]
    $generated.Add("INSERT IGNORE INTO ``$table`` ($columns) SELECT $values FROM DUAL WHERE @film_seed_required = 1;")
    $matched++
}

if ($matched -ne $insertLines.Count) {
    throw "Converted $matched of $($insertLines.Count) INSERT statements"
}

$generated.Add('')
$generated.Add('-- Fresh search-index events. Historical runtime outbox rows are excluded above; these 27 current snapshots are safe to publish once.')
$generated.Add('INSERT IGNORE INTO `outbox` (`id`, `aggregate_id`, `created_at`, `payload`, `topic`)')
$generated.Add('SELECT CONCAT(''seed-film-sync-'', f.`id`), f.`id`, CURRENT_TIMESTAMP(6),')
$generated.Add('       JSON_OBJECT(''id'', f.`id`, ''title'', f.`title`, ''thumbnailUrl'', f.`thumbnail_url`, ''thumbnailFileId'', f.`thumbnail_file_id`, ''averageRating'', f.`average_rating`, ''ratingCount'', f.`rating_count`, ''followCount'', f.`follow_count`, ''episodeCount'', f.`episode_count`, ''season'', f.`season`, ''status'', f.`status`, ''lastUpdate'', DATE_FORMAT(f.`last_update`, ''%Y-%m-%dT%H:%i:%s.%fZ'')),')
$generated.Add('       ''film.sync''')
$generated.Add('FROM `films` f WHERE @film_seed_required = 1;')
$generated.Add('')
$generated.Add('INSERT IGNORE INTO `film_seed_metadata` (`seed_key`, `applied_at`) SELECT ''catalog-2026-08-10'', CURRENT_TIMESTAMP(6) FROM DUAL WHERE @film_seed_required = 1;')
$generated.Add('SET FOREIGN_KEY_CHECKS = 1;')

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllLines($resolvedOutput, $generated, $utf8NoBom)

[pscustomobject]@{
    OutputPath = $resolvedOutput
    InsertCount = $matched
    ByteCount = (Get-Item -LiteralPath $resolvedOutput).Length
    Sha256 = (Get-FileHash -LiteralPath $resolvedOutput -Algorithm SHA256).Hash
}
