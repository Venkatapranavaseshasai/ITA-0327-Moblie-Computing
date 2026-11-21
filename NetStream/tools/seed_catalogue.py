#!/usr/bin/env python3
"""Seed only the three demo IDs. Uses Application Default Credentials, never mobile keys."""
import argparse
import json
from pathlib import Path
import firebase_admin
from firebase_admin import firestore

parser = argparse.ArgumentParser()
parser.add_argument('--project', required=True, help='Explicit Firebase project ID')
args = parser.parse_args()
app = firebase_admin.initialize_app(options={'projectId': args.project})
db = firestore.client(app)
source = Path(__file__).resolve().parents[1] / 'core/data/src/main/assets/catalogue.json'
batch = db.batch()
for row in json.loads(source.read_text()):
    video_id = row.pop('id')
    batch.set(db.collection('videos').document(video_id), row, merge=True)
batch.commit()
print(f'Seeded three videos in {args.project}/videos.')
