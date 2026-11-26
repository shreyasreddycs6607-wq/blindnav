"""Train the banknote classifier the app expects (app/src/main/assets/currency.tflite).

UNTESTED: written against MediaPipe Model Maker's documented API but never run here (no dataset, no GPU).

Setup:   pip install mediapipe-model-maker
Data:    folders named 10, 20, 50, 100, 200, 500 and none (random non-note photos), each with many photos.
         Include old and new note designs, both sides, folded, worn, held in a hand, bright and dim light, many backgrounds.
         Roboflow Universe has some Indian note sets (check each dataset's licence); you will still need your own photos.
Run:     python tools/train_currency.py path/to/dataset
Result:  currency.tflite -> copy into blindnav-android/app/src/main/assets/

Measure the confusion matrix on photos you took yourself before trusting it. Do not ship without testing 10 vs 20 vs 50
(similar colours) and every note folded.
"""
import sys

from mediapipe_model_maker import image_classifier

data = image_classifier.Dataset.from_folder(sys.argv[1])
train, rest = data.split(0.8)
val, test = rest.split(0.5)
options = image_classifier.ImageClassifierOptions(
    supported_model=image_classifier.SupportedModels.MOBILENET_V2,
    hparams=image_classifier.HParams(export_dir="currency_out"),
)
model = image_classifier.ImageClassifier.create(train_data=train, validation_data=val, options=options)
loss, acc = model.evaluate(test)
print(f"test loss {loss:.3f} accuracy {acc:.3f}")
model.export_model("currency.tflite")
