#!/bin/bash
#SBATCH --job-name=sms_spam_app
#SBATCH --partition=mtech
#SBATCH --gres=gpu:1
#SBATCH --output=/scratch/m25cse012/sms_spam_detector_v2/logs/app_%j.log
#SBATCH --nodelist=cn03
#SBATCH --time=12:00:00

set -e
export LD_LIBRARY_PATH=/opt/ohpc/apps/python/3.10.pytorch/lib:$LD_LIBRARY_PATH
source /scratch/m25cse012/myenv/bin/activate
cd /scratch/m25cse012/sms_spam_detector_v2/

# Find newest versioned model dir
LATEST=$(ls -dt final_model_*/ 2>/dev/null | head -1)
[ -z "$LATEST" ] && echo "ERROR: no versioned model dir found" && exit 1
echo "Using model: $LATEST"

echo "========================================================"
echo "Starting web UI for testing"
echo "========================================================"
# Run in the foreground so the Slurm job stays active and doesn't terminate
python -u src/app.py --config configs/config.yaml --model "$LATEST" --port 8000
