Turning Angle Function (TAF) Shape Search Engine

This project now contains a **Turning-Angle-Function (TAF) based search engine** for shape retrieval.

You can:
1. Load a query image (File -> Open Image...)
2. Index a dataset folder of candidate images (Search -> Index Folder...)
3. Run a similarity search using the **turning angle descriptor** (Search -> Search Similar ...)

You can also export the extracted contour coordinates:
- **File → Export Contours (CSV)…** saves ordered boundary points for each detected object.

A small example dataset is included in `dataset/` (square, triangle, circle, ...).

## Included multi-object demo dataset (for "perfect" outputs)

This ZIP also includes:

- `dataset_two_objects/` — **two-object images** (two black shapes on white) **plus** a copy of `BeispielMosaicBild01.jpg`.
- `queries/` — ready-to-use query images that exactly match some dataset entries.

To get a clean sanity-check result:
1. **File → Open Image…** → choose `queries/query_triangle_square.png` (or any file in `queries/`).
2. **Search → Index Folder…** → choose `dataset_two_objects/`.
3. **Search → Search Similar (Turning Angle)…**

---

## What was implemented

### 1) Robust contour extraction (ordered boundary)
To compute turning angles, we need an **ordered closed contour** (not just an unordered cloud of boundary pixels).

Implemented in `ContourTracer.traceMooreBoundary(...)`:
- Builds an object mask by thresholding (foreground = not-white)
- Finds connected components (8-connected) and keeps the **largest K components** (K configurable).
- For single-object datasets, K=1 behaves like “largest component only”.
- For mosaics / multi-object images, K>1 enables **multi-object queries** ("use both")
- Extracts the contour using **Moore-neighbor boundary tracing** (8-connected), returning a closed point list

### 2) Turning Angle Function descriptor
Implemented in `TurningAngleDescriptor`:
- Computes direction angles along the contour
- Converts them to **signed turning increments** (wrapped to [-π, π])
- Accumulates them to a **cumulative turning angle** function
- Normalizes arc length and **resamples to a fixed size** (default 128 samples)
- Normalizes total turning to ±2π to reduce pixelization drift

### 3) Search engine and GUI integration
Implemented in `ShapeSearchEngine` and wired into the GUI menu:
- `Search -> Index Folder...` indexes all images in the folder (recursive)
- `Search -> Search Similar...` computes the descriptor of the current image and ranks the index by distance
- `SearchResultsWindow` shows the top matches as thumbnails with distances
- `Search -> Clear Index` clears the in-memory index

---

## How to run

### Option A: From an IDE (NetBeans / IntelliJ / Eclipse)
Open the project and run `com.mycompany.project00.Main`.

### Option B: From command line (javac)
From the project folder:

```bash
find src/main/java -name "*.java" > sources.txt
javac @sources.txt
java -cp src/main/java com.mycompany.project00.Main
```

> Note: this is the minimal approach. If you have Maven installed, you can run it as a normal Maven project.

---

## Dataset requirements / tips

Best results when dataset images are:
- One main dark shape on a light background
- Not extremely noisy
- Roughly centered

If an image contains multiple disconnected foreground objects, the engine will extract up to **K components** (default K=5) and match them jointly.

You can tune descriptor extraction defaults inside `ShapeSearchEngine`:
- `threshold` (default 220)
- `samples` (default 128)

---

## Key files

- `ContourTracer.java` - ordered contour extraction
- `TurningAngleDescriptor.java` - descriptor + cyclic distance
- `ShapeExtractor.java` - end-to-end extraction from image
- `ShapeSearchEngine.java` - indexing + nearest-neighbor search
- `SearchResultsWindow.java` - GUI display of ranked results
- `SimpleMenuBar.java` - adds Search menu to the UI


---

## Multi-object queries ("use both")

If an image contains multiple disconnected foreground objects (e.g., the default `BeispielMosaicBild01.jpg` has a person and a jacket),
the search engine can now extract **multiple TAF descriptors** (one per connected component) and compare them jointly.

How it works:
- Extract up to **K largest connected components** from the query image and from each indexed image
- Compute pairwise cyclic TAF distances between components
- Solve a **minimum-cost one-to-one matching** (DP assignment)
- Add penalties for missing/extra components so that matches must explain **all query objects**

Parameters (see `ShapeSearchEngine`):
- `maxComponents` (default 5)
- `minComponentArea` (default 200)
- `missingComponentPenalty` (default 10.0)
- `extraComponentPenalty` (default 2.0)

If you want to force “single object only” behavior, set `maxComponents = 1`.

---

## Component count visibility (UI + console)

When you run **Search Similar**, the program now:
- prints to the console: `Query components = X, Candidate components = Y, distance = ...`
- shows `Query components: X` at the top of the results window
- shows `query components = X, candidate components = Y` inside each result tile


## New: AUTO segmentation for real-world images
The original course version assumed *black shapes on white background*.
This version adds an **AUTO** foreground mask builder that:
- estimates the background color from border pixels,
- thresholds by color-distance to background (Otsu),
- falls back to luminance-based Otsu if needed,
- and finally falls back to the legacy white-background threshold.

This improves performance on colored backgrounds (e.g., red backgrounds) and many poster-like images.

## New: Contour coordinate export
Use **File → Export Contours (CSV)…** to write ordered contour coordinates. Each row is:
`component_id,point_index,x,y`

Comment lines (`# ...`) include area, bounding box, and centroid per component.


### Segmentation mode selector
In the GUI, use **Search → Segmentation Mode** to switch between:
- `AUTO` (default): border-based background color + fallbacks
- `Border Color Distance`
- `Otsu (Luminance)`
- `Legacy White Background`

If a particular image fails in AUTO, try Otsu or Legacy.
