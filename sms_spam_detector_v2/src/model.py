"""
Model Architecture for SMS Sideloading Spam Detection.

Implements a hybrid architecture:
MuRIL (Transformer) + Auxiliary Feature Fusion (from Heuristic Engine).
"""

import torch
import torch.nn as nn
from transformers import PreTrainedModel, AutoModel, AutoConfig

class SideloadingDetectorConfig:
    def __init__(self, model_name="google/muril-base-cased", num_labels=2, aux_feature_dim=25, fusion_dim=256, dropout=0.3):
        self.model_name = model_name
        self.num_labels = num_labels
        self.aux_feature_dim = aux_feature_dim
        self.fusion_dim = fusion_dim
        self.dropout = dropout

class SideloadingDetector(nn.Module):
    """
    Hybrid model: MuRIL transformer + auxiliary heuristic features.
    """
    def __init__(self, config: SideloadingDetectorConfig):
        super(SideloadingDetector, self).__init__()
        
        # Load pre-trained MuRIL backbone
        self.backbone_config = AutoConfig.from_pretrained(config.model_name)
        self.backbone = AutoModel.from_pretrained(config.model_name)
        
        hidden_size = self.backbone_config.hidden_size # Usually 768 for base models
        
        # Auxiliary feature processing
        self.use_auxiliary = config.aux_feature_dim > 0
        if self.use_auxiliary:
            self.aux_mlp = nn.Sequential(
                nn.Linear(config.aux_feature_dim, 64),
                nn.ReLU(),
                nn.Dropout(config.dropout),
                nn.Linear(64, 64),
                nn.ReLU()
            )
            combined_dim = hidden_size + 64
        else:
            combined_dim = hidden_size
            
        # Fusion and Classification Head
        self.classifier = nn.Sequential(
            nn.Linear(combined_dim, config.fusion_dim),
            nn.ReLU(),
            nn.Dropout(config.dropout),
            nn.Linear(config.fusion_dim, config.num_labels)
        )

    def forward(self, input_ids, attention_mask, auxiliary_features=None, labels=None):
        # 1. Pass through transformer
        outputs = self.backbone(
            input_ids=input_ids,
            attention_mask=attention_mask
        )
        # Use the [CLS] token representation (pooler_output or first token)
        cls_embedding = outputs.last_hidden_state[:, 0, :]
        
        # 2. Process auxiliary features
        if self.use_auxiliary and auxiliary_features is not None:
            aux_embedding = self.aux_mlp(auxiliary_features)
            # Concatenate [CLS] and auxiliary embeddings
            combined_embedding = torch.cat((cls_embedding, aux_embedding), dim=1)
        else:
            combined_embedding = cls_embedding
            
        # 3. Classification
        logits = self.classifier(combined_embedding)
        
        loss = None
        if labels is not None:
            # Basic Cross Entropy (focal loss can be implemented in the training loop/trainer)
            loss_fct = nn.CrossEntropyLoss()
            loss = loss_fct(logits.view(-1, self.classifier[-1].out_features), labels.view(-1))
            
        return {"loss": loss, "logits": logits} if loss is not None else {"logits": logits}
