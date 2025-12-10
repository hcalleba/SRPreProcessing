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

# Test 6: Globenet
Write-Host "Globenet - 1" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Globenet.graph" `
    -demands "$DATA\Globenet.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 10 `
    -perturbationPercent 0.5 `
    > results\Globenet_1.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

Write-Host "Globenet - 2" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Globenet.graph" `
    -demands "$DATA\Globenet.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 67 `
    -perturbationPercent 0.5 `
    > results\Globenet_2.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

Write-Host "Globenet - 3" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Globenet.graph" `
    -demands "$DATA\Globenet.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 442 `
    -perturbationPercent 0.5 `
    > results\Globenet_3.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

# Test 7 Esnet
Write-Host "Esnet - 1" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Esnet.graph" `
    -demands "$DATA\Esnet.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 10 `
    -perturbationPercent 0.5 `
    > results\Esnet_1.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

Write-Host "Esnet - 2" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Esnet.graph" `
    -demands "$DATA\Esnet.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 68 `
    -perturbationPercent 0.5 `
    > results\Esnet_2.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

Write-Host "Esnet - 3" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Esnet.graph" `
    -demands "$DATA\Esnet.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 455 `
    -perturbationPercent 0.5 `
    > results\Esnet_3.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

# Test 8: Uninett2011
Write-Host "Uninett2011 - 1" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Uninett2011.graph" `
    -demands "$DATA\Uninett2011.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 10 `
    -perturbationPercent 0.5 `
    > results\Uninett2011_1.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

Write-Host "Uninett2011 - 2" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Uninett2011.graph" `
    -demands "$DATA\Uninett2011.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 69 `
    -perturbationPercent 0.5 `
    > results\Uninett2011_2.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

Write-Host "Uninett2011 - 3" -ForegroundColor Cyan
java -Xmx16G -cp $RCP edu.repetita.main.Main `
    -graph "$DATA\Uninett2011.graph" `
    -demands "$DATA\Uninett2011.0000.demands" `
    -numAdversarialMatrices 20 `
    -maxPerturbedDemands 469 `
    -perturbationPercent 0.5 `
    > results\Uninett2011_3.txt
Write-Host "Done" -ForegroundColor Green
Write-Host ""

Write-Host "All tests completed! Results in results\" -ForegroundColor Green