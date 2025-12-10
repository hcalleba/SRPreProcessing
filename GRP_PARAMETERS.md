# GRP Solver - Configurable Parameters

The GRP solver now supports runtime configuration of adversarial matrix generation parameters without requiring recompilation.

## Available Parameters

### `-numAdversarialMatrices <value>`
- **Description**: Number of adversarial matrices to generate
- **Type**: Integer
- **Default**: 10
- **Example**: `-numAdversarialMatrices 15`

### `-maxPerturbedDemands <value>`
- **Description**: Maximum number of demands that can be perturbed per matrix
- **Type**: Integer
- **Default**: 5
- **Example**: `-maxPerturbedDemands 8`

### `-perturbationPercent <value>`
- **Description**: Perturbation percentage as a decimal value (e.g., 0.20 for 20%)
- **Type**: Double
- **Default**: 0.20
- **Example**: `-perturbationPercent 0.15`

## Usage Examples

### Basic usage with default parameters:
```bash
java -cp <classpath> edu.repetita.main.Main -graph topology.graph -demands demands.demands -t 300
```

### Custom number of adversarial matrices:
```bash
java -cp <classpath> edu.repetita.main.Main -graph topology.graph -demands demands.demands -t 300 -numAdversarialMatrices 20
```

### Custom perturbation parameters:
```bash
java -cp <classpath> edu.repetita.main.Main -graph topology.graph -demands demands.demands -t 300 -maxPerturbedDemands 10 -perturbationPercent 0.25
```

### All parameters customized:
```bash
java -cp <classpath> edu.repetita.main.Main -graph topology.graph -demands demands.demands -t 300 -numAdversarialMatrices 15 -maxPerturbedDemands 8 -perturbationPercent 0.15
```

## Notes

- Parameters can be specified in any order on the command line
- If a parameter is not specified, the default value will be used
- The perturbation percentage should be specified as a decimal (0.20 = 20%)
- No recompilation is needed when changing these parameters

