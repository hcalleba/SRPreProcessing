# Simple script to compile and run tests
# Usage: .\run_tests.ps1

Write-Host "Compiling..." -ForegroundColor Yellow

# Compile
$CP = "lib\commons-lang-2.6.jar;lib\gurobi.jar"
javac -cp $CP -d bin (Get-ChildItem -Path src -Filter *.java -Recurse | Select-Object -ExpandProperty FullName)

Write-Host "Running tests..." -ForegroundColor Green
Write-Host ""

# Run classpath
$RCP = "bin;lib\commons-lang-2.6.jar;lib\gurobi.jar"
$DATA = "data\2016TopologyZooUCL_inverseCapacity"

# Create results directory
New-Item -ItemType Directory -Force -Path "results" | Out-Null

# Test 1: Aarnet
Write-Host "Aarnet" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Aarnet.graph" `
    -demands "$DATA\Aarnet.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 38 `
    -perturbationPercent 0.5 `
    > results\Aarnet_5.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

# Test 2: BtEurope
Write-Host "BtEurope" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\BtEurope.graph" `
    -demands "$DATA\BtEurope.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 48 `
    -perturbationPercent 0.5 `
    > results\BtEurope_5.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

# Test 3: Geant2012
Write-Host "Geant2012" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Geant2012.graph" `
    -demands "$DATA\Geant2012.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 80 `
    -perturbationPercent 0.5 `
    > results\Geant2012_5.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

# Test 4: Garr201201
Write-Host "Garr201201" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Garr201201.graph" `
    -demands "$DATA\Garr201201.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 122 `
    -perturbationPercent 0.5 `
    > results\Garr201201_5.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

# Test 5: Missouri
Write-Host "Missouri" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Missouri.graph" `
    -demands "$DATA\Missouri.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 134 `
    -perturbationPercent 0.5 `
    > results\Missouri_5.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

# Test 6: Globenet
Write-Host "Globenet" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Globenet.graph" `
    -demands "$DATA\Globenet.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 134 `
    -perturbationPercent 0.5 `
    > results\Globenet_5.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

# Test 7 Esnet
Write-Host "Esnet" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Esnet.graph" `
    -demands "$DATA\Esnet.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 136 `
    -perturbationPercent 0.5 `
    > results\Esnet_5.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

# Test 8: Uninett2011
Write-Host "Uninett2011" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Uninett2011.graph" `
    -demands "$DATA\Uninett2011.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 138 `
    -perturbationPercent 0.5 `
    > results\Uninett2011_5.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

Write-Host "All tests completed! Results in results\" -ForegroundColor Green