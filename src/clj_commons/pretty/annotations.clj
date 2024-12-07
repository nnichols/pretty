(ns clj-commons.pretty.annotations
  "Tools to annotate a line of source code, in the form of callouts (lines and arrows) connected to a message.

      SELECT DATE, AMT FROM PAYMENTS WHEN AMT > 10000
                   ▲▲▲               ▲▲▲▲
                   │                 │
                   │                 └╴ Unknown token
                   │
                   └╴ Invalid column name

  This kind of output is common with various kinds of parsers or interpreters.

  Specs for types and functions are in the [[spec]] namespace."
  {:added "3.3.0"})

(def default-style
  "The default style used when generating callouts.

  Key       | Default | Description
  ---       |---      |---
  :font     | :yellow | Default font characteristics if not overrided by annotation
  :spacing  | :tall   | One of :tall, :compact, or :minimal
  :marker   | \"▲\"   | The marker character used to identify the offset/length of an annotation
  :bar      | \"│\"   | Character used as the vertical bar in the callout
  :nib      | \"└╴ \" | String used just before the annotation's message

  When :spacing is :minimal, only the lines with markers or error messages appear
  (the lines with just vertical bars are omitted).  :compact spacing is the same, but
  one line of bars appears between the markers and the first annotation message.

  Note: rendering of Unicode characters in HTML often uses incorrect fonts or adds unwanted
  character spacing; the annotations look proper in console output."
  {:font :yellow
   :spacing :tall
   :marker "▲"
   :bar "│"
   :nib "└╴ "})

(def ^:dynamic *default-style*
  "The default style used when no style is provided; some applications may bind or
   override this."
  default-style)

(defn- nchars
  [n ch]
  (apply str (repeat n ch)))

(defn- markers
  [style annotations]
  (let [{:keys [font marker]} style]
    (loop [output-offset 0
           annotations annotations
           result [font]]
      (if-not annotations
        result
        (let [{:keys [offset length font]
               :or {length 1}} (first annotations)
              spaces-needed (- offset output-offset)
              result' (conj result
                            (nchars spaces-needed \space)
                            [font (nchars length marker)])]
          (recur (+ offset length)
                 (next annotations)
                 result'))))))

(defn- bars
  [style annotations]
  (let [{:keys [font bar]} style]
    (loop [output-offset 0
           annotations annotations
           result [font]]
      (if-not annotations
        result
        (let [{:keys [offset font]} (first annotations)
              spaces-needed (- offset output-offset)
              result' (conj result
                            (nchars spaces-needed \space)
                            [font bar])]
          (recur (+ offset 1)
                 (next annotations)
                 result'))))))

(defn- bars+message
  [style annotations]
  (let [{:keys [font bar nib]} style]
    (loop [output-offset 0
           [annotation & more-annotations] annotations
           result [font]]
      (let [{:keys [offset font message]} annotation
            spaces-needed (- offset output-offset)
            last? (not (seq more-annotations))
            result' (conj result
                          (nchars spaces-needed \space)
                          [font
                           (if last?
                             nib
                             bar)
                           (when last?
                             message)])]
        (if last?
          result'
          (recur (+ offset 1)
                 more-annotations
                 result'))))))

(defn callouts
  "Creates callouts (the marks, bars, and messages from the example) from annotations.

  Each annotation is a map:

  Key       | Description
  ---       |---
  :message  | Composed string of the message to present
  :offset   | Integer position (from 0) to mark on the line
  :length   | Number of characters in the marker (min 1, defaults to 1)
  :font     | Override of the style's font; used for marker, bars, nib, and message


  At least one annotation is required; they will be sorted into an appropriate order.
  Annotation's ranges should not overlap.

  The messages should be relatively short, and not contain any line breaks.

  Returns a sequence of composed strings, one for each line of output.

  The calling code is responsible for any output; even the line being annotated;
  this might look something like:

      (ansi/perr source-line)
      (run! ansi/perr (annotations/annotate annotations))

  Uses the style defined by [[*default-style*]] if no style is provided."
  ([annotations]
   (callouts  *default-style* annotations))
  ([style annotations]
   ;; TODO: Check for overlaps
   (let [expanded (sort-by :offset annotations)
         {:keys [spacing]} style
         marker-line (markers style expanded)]
     (loop [annotations expanded
            first? true
            result [marker-line]]
       (let [include-bars? (or (= spacing :tall)
                               (and first? (= spacing :compact)))
             result' (conj result
                           (when include-bars?
                             (bars style annotations))
                           (bars+message style annotations))
             annotations' (butlast annotations)]
         (if (seq annotations')
           (recur annotations' false result')
           (remove nil? result')))))))
