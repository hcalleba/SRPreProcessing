<#
Usage examples:
  # Run every test
  .\run_all_tests.ps1

  # List available tests
  .\run_all_tests.ps1 -ListOnly
#>

param(
    [switch]$ListOnly
)

Write-Host "Compiling..." -ForegroundColor Yellow

# Classpath for compilation (same as original scripts)
$CP = "lib\commons-lang-2.6.jar;lib\gurobi.jar"
# Compile all Java sources under src into bin
javac -cp $CP -d bin (Get-ChildItem -Path src -Filter *.java -Recurse | Select-Object -ExpandProperty FullName)

Write-Host "Running tests..." -ForegroundColor Green
Write-Host ""

# Runtime classpath
$RCP = "bin;lib\commons-lang-2.6.jar;lib\gurobi.jar"
$DATA = "data\2016TopologyZooUCL_inverseCapacity"

# Ensure results directory exists (same as originals)
New-Item -ItemType Directory -Force -Path (Join-Path $PSScriptRoot "results") | Out-Null

# Define every test invocation found across the four original scripts.
# Each entry preserves: Name (output filename), Graph, Demands, MaxPerturbedDemands
$tests = @(
    @{Name='Aarnet_1'; Graph='Aarnet.graph'; Demands='Aarnet.0000.demands'; MaxPerturbed='10'},
    @{Name='Aarnet_2'; Graph='Aarnet.graph'; Demands='Aarnet.0000.demands'; MaxPerturbed='19'},
    @{Name='Aarnet_3'; Graph='Aarnet.graph'; Demands='Aarnet.0000.demands'; MaxPerturbed='34'},
    @{Name='Aarnet_4'; Graph='Aarnet.graph'; Demands='Aarnet.0000.demands'; MaxPerturbed='38'},

    @{Name='BtEurope_1'; Graph='BtEurope.graph'; Demands='BtEurope.0000.demands'; MaxPerturbed='10'},
    @{Name='BtEurope_2'; Graph='BtEurope.graph'; Demands='BtEurope.0000.demands'; MaxPerturbed='24'},
    @{Name='BtEurope_3'; Graph='BtEurope.graph'; Demands='BtEurope.0000.demands'; MaxPerturbed='55'},
    @{Name='BtEurope_4'; Graph='BtEurope.graph'; Demands='BtEurope.0000.demands'; MaxPerturbed='48'},
#
    @{Name='Geant2012_1'; Graph='Geant2012.graph'; Demands='Geant2012.0000.demands'; MaxPerturbed='10'},
    @{Name='Geant2012_2'; Graph='Geant2012.graph'; Demands='Geant2012.0000.demands'; MaxPerturbed='40'},
    @{Name='Geant2012_3'; Graph='Geant2012.graph'; Demands='Geant2012.0000.demands'; MaxPerturbed='156'},
    @{Name='Geant2012_4'; Graph='Geant2012.graph'; Demands='Geant2012.0000.demands'; MaxPerturbed='80'}
#
#     @{Name='Garr201201_1'; Graph='Garr201201.graph'; Demands='Garr201201.0000.demands'; MaxPerturbed='10'},
#     @{Name='Garr201201_2'; Graph='Garr201201.graph'; Demands='Garr201201.0000.demands'; MaxPerturbed='61'},
#     @{Name='Garr201201_3'; Graph='Garr201201.graph'; Demands='Garr201201.0000.demands'; MaxPerturbed='366'},
#     @{Name='Garr201201_4'; Graph='Garr201201.graph'; Demands='Garr201201.0000.demands'; MaxPerturbed='122'},
#
#     @{Name='Missouri_1'; Graph='Missouri.graph'; Demands='Missouri.0000.demands'; MaxPerturbed='10'},
#     @{Name='Missouri_2'; Graph='Missouri.graph'; Demands='Missouri.0000.demands'; MaxPerturbed='67'},
#     @{Name='Missouri_3'; Graph='Missouri.graph'; Demands='Missouri.0000.demands'; MaxPerturbed='442'},
#     @{Name='Missouri_4'; Graph='Missouri.graph'; Demands='Missouri.0000.demands'; MaxPerturbed='134'},
#
#     @{Name='Globenet_1'; Graph='Globenet.graph'; Demands='Globenet.0000.demands'; MaxPerturbed='10'},
#     @{Name='Globenet_2'; Graph='Globenet.graph'; Demands='Globenet.0000.demands'; MaxPerturbed='67'},
#     @{Name='Globenet_3'; Graph='Globenet.graph'; Demands='Globenet.0000.demands'; MaxPerturbed='442'},
#     @{Name='Globenet_4'; Graph='Globenet.graph'; Demands='Globenet.0000.demands'; MaxPerturbed='134'},
#
#     @{Name='Esnet_1'; Graph='Esnet.graph'; Demands='Esnet.0000.demands'; MaxPerturbed='10'},
#     @{Name='Esnet_2'; Graph='Esnet.graph'; Demands='Esnet.0000.demands'; MaxPerturbed='68'},
#     @{Name='Esnet_3'; Graph='Esnet.graph'; Demands='Esnet.0000.demands'; MaxPerturbed='455'},
#     @{Name='Esnet_4'; Graph='Esnet.graph'; Demands='Esnet.0000.demands'; MaxPerturbed='136'},
#
#     @{Name='Uninett2011_1'; Graph='Uninett2011.graph'; Demands='Uninett2011.0000.demands'; MaxPerturbed='10'},
#     @{Name='Uninett2011_2'; Graph='Uninett2011.graph'; Demands='Uninett2011.0000.demands'; MaxPerturbed='69'},
#     @{Name='Uninett2011_3'; Graph='Uninett2011.graph'; Demands='Uninett2011.0000.demands'; MaxPerturbed='469'},
#     @{Name='Uninett2011_4'; Graph='Uninett2011.graph'; Demands='Uninett2011.0000.demands'; MaxPerturbed='138'}
)

# If ListOnly is requested, print available test names and exit
if ($ListOnly) {
    Write-Host "Available tests:" -ForegroundColor Cyan
    foreach ($test in $tests) {
        Write-Host ("- " + $test.Name)
    }
    return
}

# Run all tests
$runset = $tests

# Helper to run a single test entry
function Run-Test($entry) {
    $testName = $entry.Name
    $graphFile = $entry.Graph
    $demandsFile = $entry.Demands
    $maxPerturbed = $entry.MaxPerturbed

    $outFile = Join-Path $PSScriptRoot (Join-Path 'results' ("$testName.txt"))
    Write-Host "Running $testName -> $outFile" -ForegroundColor Cyan

    $javaArgs = @(
        "-Xmx16G",
        "-cp", $RCP,
        "edu.repetita.main.Main",
        "-graph", (Join-Path $DATA $graphFile),
        "-demands", (Join-Path $DATA $demandsFile),
        "-numAdversarialMatrices", "20",
        "-maxPerturbedDemands", $maxPerturbed,
        "-perturbationPercent", "0.5"
    )

    # Use Start-Process and redirect stdout to preserve the same behavior as original scripts
    Start-Process -FilePath "java" -ArgumentList $javaArgs -NoNewWindow -Wait -RedirectStandardOutput $outFile

    Write-Host "Done" -ForegroundColor Green
    Write-Host ""
}

# Execute all selected tests in order
foreach ($t in $runset) {
    Run-Test $t
}

Write-Host "All tests completed! Results in results\" -ForegroundColor Green

