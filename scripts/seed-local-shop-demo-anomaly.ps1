[CmdletBinding()]
param(
    [string]$DatabasePath = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Get-H2JarPath {
    $repositoryRoot = Join-Path $env:USERPROFILE ".m2\repository\com\h2database\h2"
    $jar =
        Get-ChildItem $repositoryRoot -Recurse -Filter "h2-*.jar" -ErrorAction Stop |
        Sort-Object FullName -Descending |
        Select-Object -First 1 -ExpandProperty FullName
    if (-not $jar) {
        throw "Could not find an H2 JDBC jar under $repositoryRoot. Run `mvn test` once first."
    }
    return $jar
}

function Format-SqlTimestamp([DateTimeOffset]$Value) {
    return $Value.UtcDateTime.ToString("yyyy-MM-dd HH:mm:ss", [System.Globalization.CultureInfo]::InvariantCulture)
}

if ([string]::IsNullOrWhiteSpace($DatabasePath)) {
    $DatabasePath = Join-Path $PSScriptRoot "..\output\demo-db\shop-demo"
}

$resolvedDatabasePath = [System.IO.Path]::GetFullPath($DatabasePath)
$databaseDir = Split-Path -Parent $resolvedDatabasePath
$databaseName = Split-Path -Leaf $resolvedDatabasePath
$sqlPath = Join-Path $databaseDir "$databaseName-seed.sql"
$h2Jar = Get-H2JarPath

New-Item -ItemType Directory -Path $databaseDir -Force | Out-Null
Remove-Item @(
    "$resolvedDatabasePath.mv.db",
    "$resolvedDatabasePath.trace.db",
    "$resolvedDatabasePath.lock.db",
    $sqlPath
) -Force -ErrorAction SilentlyContinue

$jdbcPath = $resolvedDatabasePath -replace "\\", "/"
$jdbcUrl = "jdbc:h2:file:$jdbcPath;MODE=MYSQL;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1"

$now = [DateTimeOffset]::UtcNow
$currentStart = $now.AddHours(-24)
$previousStart = $currentStart.AddHours(-24)

$sqlLines = New-Object 'System.Collections.Generic.List[string]'

$orderId = 100000
$itemId = 500000
$refundId = 900000
$previousOrders = New-Object System.Collections.ArrayList
$currentOrders = New-Object System.Collections.ArrayList

function Add-OrderRecord {
    param(
        [DateTimeOffset]$CreatedAt,
        [string]$Channel,
        [string]$Region,
        [string]$Category,
        [decimal]$UnitPrice,
        [int]$Quantity,
        [System.Collections.ArrayList]$Bucket
    )

    $script:orderId++
    $script:itemId++

    $currentOrderId = $script:orderId
    $currentItemId = $script:itemId
    $userId = 1000 + (($currentOrderId + $currentItemId) % 20)
    $productId = 2000 + (($currentItemId + $currentOrderId) % 8)
    $amount = [decimal]($UnitPrice * $Quantity)
    $timestampText = Format-SqlTimestamp $CreatedAt
    $amountText = $amount.ToString("0.00", [System.Globalization.CultureInfo]::InvariantCulture)
    $unitPriceText = $UnitPrice.ToString("0.00", [System.Globalization.CultureInfo]::InvariantCulture)

    $sqlLines.Add(
        "insert into orders(order_id, user_id, channel, region, created_at, payment_amount, status) values ($currentOrderId, $userId, '$Channel', '$Region', timestamp '$timestampText', $amountText, 'paid');")
    $sqlLines.Add(
        "insert into order_items(item_id, order_id, product_id, quantity, unit_price, category, created_at) values ($currentItemId, $currentOrderId, $productId, $Quantity, $unitPriceText, '$Category', timestamp '$timestampText');")

    [void]$Bucket.Add([pscustomobject]@{
        OrderId = $currentOrderId
        Amount = $amount
    })
}

function Add-WindowOrders {
    param(
        [DateTimeOffset]$WindowStart,
        [DateTimeOffset]$WindowEnd,
        [int]$Count,
        [string]$Channel,
        [string]$Region,
        [string]$Category,
        [decimal]$BasePrice,
        [System.Collections.ArrayList]$Bucket
    )

    $windowMinutes = [Math]::Max([int][Math]::Floor(($WindowEnd - $WindowStart).TotalMinutes) - 1, 1)
    for ($i = 0; $i -lt $Count; $i++) {
        $offsetMinutes = ($i * 17) % $windowMinutes
        $createdAt = $WindowStart.AddMinutes($offsetMinutes)
        $quantity = 1 + ($i % 2)
        $unitPrice = $BasePrice + [decimal](($i % 5) * 7.5)
        Add-OrderRecord `
            -CreatedAt $createdAt `
            -Channel $Channel `
            -Region $Region `
            -Category $Category `
            -UnitPrice $unitPrice `
            -Quantity $quantity `
            -Bucket $Bucket
    }
}

function Add-RefundRecords {
    param(
        [System.Collections.ArrayList]$Orders,
        [int]$Count,
        [DateTimeOffset]$WindowStart,
        [DateTimeOffset]$WindowEnd,
        [string]$Reason
    )

    if ($Orders.Count -eq 0) {
        return
    }

    $windowMinutes = [Math]::Max([int][Math]::Floor(($WindowEnd - $WindowStart).TotalMinutes) - 1, 1)
    for ($i = 0; $i -lt $Count; $i++) {
        $script:refundId++
        $order = $Orders[$i % $Orders.Count]
        $offsetMinutes = ($i * 29) % $windowMinutes
        $createdAt = $WindowStart.AddMinutes($offsetMinutes)
        $timestampText = Format-SqlTimestamp $createdAt
        $refundAmount = ([decimal]$order.Amount * [decimal]0.55).ToString("0.00", [System.Globalization.CultureInfo]::InvariantCulture)
        $sqlLines.Add(
            "insert into refunds(refund_id, order_id, refund_reason, created_at, refund_amount) values ($script:refundId, $($order.OrderId), '$Reason', timestamp '$timestampText', $refundAmount);")
    }
}

$sqlLines.Add("drop all objects;")
$sqlLines.Add(
    "create table users (user_id bigint primary key, user_name varchar(64) not null, segment varchar(32) not null, region varchar(32) not null, created_at timestamp not null);")
$sqlLines.Add(
    "create table products (product_id bigint primary key, product_name varchar(64) not null, category varchar(32) not null, list_price decimal(10,2) not null, created_at timestamp not null);")
$sqlLines.Add(
    "create table orders (order_id bigint primary key, user_id bigint not null, channel varchar(32) not null, region varchar(32) not null, created_at timestamp not null, payment_amount decimal(10,2) not null, status varchar(32) not null);")
$sqlLines.Add(
    "create table order_items (item_id bigint primary key, order_id bigint not null, product_id bigint not null, quantity int not null, unit_price decimal(10,2) not null, category varchar(32) not null, created_at timestamp not null);")
$sqlLines.Add(
    "create table refunds (refund_id bigint primary key, order_id bigint not null, refund_reason varchar(64) not null, created_at timestamp not null, refund_amount decimal(10,2) not null);")

$userSeedTime = Format-SqlTimestamp $previousStart.AddDays(-30)
for ($i = 0; $i -lt 20; $i++) {
    $userId = 1000 + $i
    $segment = @("new", "vip", "repeat")[$i % 3]
    $region = @("north", "east", "south", "west")[$i % 4]
    $sqlLines.Add(
        "insert into users(user_id, user_name, segment, region, created_at) values ($userId, 'user_$userId', '$segment', '$region', timestamp '$userSeedTime');")
}

$productSeedTime = Format-SqlTimestamp $previousStart.AddDays(-20)
$products = @(
    @{ ProductId = 2000; Name = "air-fryer"; Category = "home"; Price = 299.00 },
    @{ ProductId = 2001; Name = "floor-lamp"; Category = "home"; Price = 159.00 },
    @{ ProductId = 2002; Name = "face-serum"; Category = "beauty"; Price = 129.00 },
    @{ ProductId = 2003; Name = "protein-bar"; Category = "snacks"; Price = 39.00 },
    @{ ProductId = 2004; Name = "wireless-earbuds"; Category = "electronics"; Price = 399.00 },
    @{ ProductId = 2005; Name = "portable-fan"; Category = "home"; Price = 79.00 },
    @{ ProductId = 2006; Name = "desk-mat"; Category = "office"; Price = 49.00 },
    @{ ProductId = 2007; Name = "sport-bottle"; Category = "fitness"; Price = 59.00 }
)
foreach ($product in $products) {
    $listPrice = ([decimal]$product.Price).ToString("0.00", [System.Globalization.CultureInfo]::InvariantCulture)
    $sqlLines.Add(
        "insert into products(product_id, product_name, category, list_price, created_at) values ($($product.ProductId), '$($product.Name)', '$($product.Category)', $listPrice, timestamp '$productSeedTime');")
}

$previousOrganicOrders = 48
$previousAdsOrders = 36
$previousLiveOrders = 24
$currentOrganicOrders = 12
$currentAdsOrders = 30
$currentLiveOrders = 18
$previousRefunds = 4
$currentRefunds = 12

Add-WindowOrders -WindowStart $previousStart -WindowEnd $currentStart -Count $previousOrganicOrders -Channel "organic" -Region "east" -Category "home" -BasePrice 119.00 -Bucket $previousOrders
Add-WindowOrders -WindowStart $previousStart -WindowEnd $currentStart -Count $previousAdsOrders -Channel "ads" -Region "north" -Category "beauty" -BasePrice 139.00 -Bucket $previousOrders
Add-WindowOrders -WindowStart $previousStart -WindowEnd $currentStart -Count $previousLiveOrders -Channel "live" -Region "south" -Category "electronics" -BasePrice 169.00 -Bucket $previousOrders

Add-WindowOrders -WindowStart $currentStart -WindowEnd $now -Count $currentOrganicOrders -Channel "organic" -Region "east" -Category "home" -BasePrice 119.00 -Bucket $currentOrders
Add-WindowOrders -WindowStart $currentStart -WindowEnd $now -Count $currentAdsOrders -Channel "ads" -Region "north" -Category "beauty" -BasePrice 139.00 -Bucket $currentOrders
Add-WindowOrders -WindowStart $currentStart -WindowEnd $now -Count $currentLiveOrders -Channel "live" -Region "south" -Category "electronics" -BasePrice 169.00 -Bucket $currentOrders

Add-RefundRecords -Orders $previousOrders -Count $previousRefunds -WindowStart $previousStart -WindowEnd $currentStart -Reason "shipping_delay"
Add-RefundRecords -Orders $currentOrders -Count $currentRefunds -WindowStart $currentStart -WindowEnd $now -Reason "quality_issue"

$sqlLines.Add("create index idx_orders_created_at on orders(created_at);")
$sqlLines.Add("create index idx_orders_channel on orders(channel);")
$sqlLines.Add("create index idx_refunds_created_at on refunds(created_at);")

$sqlLines | Set-Content -Path $sqlPath -Encoding ascii

& java -cp $h2Jar org.h2.tools.RunScript -url $jdbcUrl -user sa -script $sqlPath

$previousStartText = Format-SqlTimestamp $previousStart
$currentStartText = Format-SqlTimestamp $currentStart
$nowText = Format-SqlTimestamp $now

& java -cp $h2Jar org.h2.tools.Shell -url $jdbcUrl -user sa -sql @"
select count(*) as orders_total from orders;
select count(*) as refunds_total from refunds;
select
  sum(case when created_at >= timestamp '$currentStartText' and created_at < timestamp '$nowText' then 1 else 0 end) as current_orders,
  sum(case when created_at >= timestamp '$previousStartText' and created_at < timestamp '$currentStartText' then 1 else 0 end) as previous_orders
from orders;
select
  sum(case when created_at >= timestamp '$currentStartText' and created_at < timestamp '$nowText' then 1 else 0 end) as current_refunds,
  sum(case when created_at >= timestamp '$previousStartText' and created_at < timestamp '$currentStartText' then 1 else 0 end) as previous_refunds
from refunds;
select channel, count(*) as orders_total from orders group by channel order by orders_total desc;
"@

$previousOrderTotal = $previousOrganicOrders + $previousAdsOrders + $previousLiveOrders
$currentOrderTotal = $currentOrganicOrders + $currentAdsOrders + $currentLiveOrders
$previousRefundRate = $previousRefunds / $previousOrderTotal
$currentRefundRate = $currentRefunds / $currentOrderTotal

Write-Host ""
Write-Host "Seeded local H2 business database:" -ForegroundColor Green
Write-Host "  jdbc-url: $jdbcUrl"
Write-Host "  orders(previous 24h): $previousOrderTotal"
Write-Host "  orders(current 24h):  $currentOrderTotal"
Write-Host "  refunds(previous 24h): $previousRefunds"
Write-Host "  refunds(current 24h):  $currentRefunds"
Write-Host ("  refund-rate(previous/current): {0:p1} -> {1:p1}" -f $previousRefundRate, $currentRefundRate)
Write-Host "  largest channel drop: organic ($previousOrganicOrders -> $currentOrganicOrders)"
Write-Host ""
Write-Host "Next step:" -ForegroundColor Yellow
Write-Host "  Start the app with scripts/local-shop-demo.datasource.yml as an extra Spring config location."
