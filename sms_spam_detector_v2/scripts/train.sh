#!/bin/bash
# ============================================================
# Full Training Pipeline
# ============================================================
#SBATCH --job-name=sms_spam_indicbert
#SBATCH --partition=mtech
#SBATCH --gres=gpu:1
#SBATCH --output=/scratch/<USERNAME>/sms_spam_detector_v2/logs/train_%j.log
#SBATCH --nodelist=cn03
#SBATCH --time=12:00:00
'''
set -e
export LD_LIBRARY_PATH=/opt/ohpc/apps/python/3.10.pytorch/lib:$LD_LIBRARY_PATH
source /scratch/<USERNAME>/myenv/bin/activate
cd /scratch/<USERNAME>/sms_spam_detector_v2/

export HF_HOME=/scratch/<USERNAME>/hf_cache
export HF_DATASETS_OFFLINE=1
export HF_HUB_OFFLINE=1

echo "========================================================"
echo "Phase 1: Train (versioned output dir, optimal threshold)"
echo "========================================================"
python -u src/train.py --config configs/config.yaml

# Find newest versioned model dir
LATEST=$(ls -dt final_model_*/ 2>/dev/null | head -1)
[ -z "$LATEST" ] && echo "ERROR: no versioned model dir found" && exit 1
echo "Latest model: $LATEST"

echo "========================================================"
echo "Phase 2: Evaluate on internal test set"
echo "========================================================"
#python -u src/evaluate.py --config configs/config.yaml --model_path "$LATEST"

echo "========================================================"
echo "Phase 3: Evaluate on external datasets"
echo "========================================================"
#for csv in data/raw/*.csv; do
 # echo "  Evaluating on $csv"
  #python -u src/evaluate.py --config configs/config.yaml \
   # --model_path "$LATEST" --custom_test "$csv"
#done

echo "========================================================"
echo "Phase 4: Quantize for mobile deployment"
echo "========================================================"
#python -u src/quantize.py --config configs/config.yaml \
#  --model_path "$LATEST" --output_dir "${LATEST}_quantized"
'''
'''echo "========================================================"
echo "Phase 5: Cluster spam messages by fraud type"
echo "========================================================"
for csv in data/raw/*.csv; do
  python -u src/cluster_spam.py \
    --input "$csv" --output data/clusters/ \
    --label_col true_label --n_clusters 12
  break  # cluster on first CSV found
done
'''
echo "========================================================"
echo "Phase 6: Start web UI for Android testing"
echo "========================================================"
python -u src/app.py --config configs/config.yaml --model "$LATEST" --port 8000 &
echo "Web UI started. Open http://$(hostname -I | awk '{print $1}'):8000 on Android."
echo ""
echo "Pipeline complete."
echo "  Model    : $LATEST"
echo "  Clusters : data/clusters/"
