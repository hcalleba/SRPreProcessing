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
Write-Host "Aarnet - 1" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Aarnet.graph" `
    -demands "$DATA\Aarnet.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 10 `
    -perturbationPercent 0.5 `
    > results\Aarnet_1.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

Write-Host "Aarnet - 2" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Aarnet.graph" `
    -demands "$DATA\Aarnet.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 19 `
    -perturbationPercent 0.5 `
    > results\Aarnet_2.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

Write-Host "Aarnet - 3" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Aarnet.graph" `
    -demands "$DATA\Aarnet.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 34 `
    -perturbationPercent 0.5 `
    > results\Aarnet_3.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

# Test 2: BtEurope
Write-Host "BtEurope - 1" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\BtEurope.graph" `
    -demands "$DATA\BtEurope.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 10 `
    -perturbationPercent 0.5 `
    > results\BtEurope_1.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

Write-Host "BtEurope - 2" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\BtEurope.graph" `
    -demands "$DATA\BtEurope.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 24 `
    -perturbationPercent 0.5 `
    > results\BtEurope_2.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

Write-Host "BtEurope - 3" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\BtEurope.graph" `
    -demands "$DATA\BtEurope.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 55 `
    -perturbationPercent 0.5 `
    > results\BtEurope_3.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

# Test 3: Geant2012
Write-Host "Geant2012 - 1" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Geant2012.graph" `
    -demands "$DATA\Geant2012.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 10 `
    -perturbationPercent 0.5 `
    > results\Geant2012_1.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

Write-Host "Geant2012 - 2" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Geant2012.graph" `
    -demands "$DATA\Geant2012.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 40 `
    -perturbationPercent 0.5 `
    > results\Geant2012_2.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

Write-Host "Geant2012 - 3" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Geant2012.graph" `
    -demands "$DATA\Geant2012.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 156 `
    -perturbationPercent 0.5 `
    > results\Geant2012_3.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

# Test 4: Garr201201
Write-Host "Garr201201 - 1" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Garr201201.graph" `
    -demands "$DATA\Garr201201.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 10 `
    -perturbationPercent 0.5 `
    > results\Garr201201_1.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

Write-Host "Garr201201 - 2" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Garr201201.graph" `
    -demands "$DATA\Garr201201.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 10 `
    -perturbationPercent 0.5 `
    > results\Garr201201_2.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

Write-Host "Garr201201 - 3" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Garr201201.graph" `
    -demands "$DATA\Garr201201.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 10 `
    -perturbationPercent 0.5 `
    > results\Garr201201_3.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

# Test 5: Missouri
Write-Host "Missouri - 1" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Missouri.graph" `
    -demands "$DATA\Missouri.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 10 `
    -perturbationPercent 0.5 `
    > results\Missouri_1.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

Write-Host "Missouri - 2" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Missouri.graph" `
    -demands "$DATA\Missouri.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 67 `
    -perturbationPercent 0.5 `
    > results\Missouri_2.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

Write-Host "Missouri - 3" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Missouri.graph" `
    -demands "$DATA\Missouri.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 442 `
    -perturbationPercent 0.5 `
    > results\Missouri_3.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

Write-Host "All tests completed! Results in results\" -ForegroundColor Green