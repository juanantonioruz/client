(ns dots-game.ex1
  (:require
   [cljs.core.async :as async
    :refer [<! >! chan close! sliding-buffer put! alts! timeout]]
   [jayq.core :refer [$ append ajax inner css $deferred
                      when done resolve pipe on bind attr
                      offset] :as jq]
   [jayq.util :refer [log]]
   [crate.core :as crate])
  (:require-macros [cljs.core.async.macros :as m :refer [go]]))



(defn xy-message [ch msg-name xy-obj]
  (put! ch [msg-name {:x (.-pageX xy-obj) :y (.-pageY xy-obj)}]))

(defn touch-xy-message [ch msg-name xy-obj]
  (xy-message ch msg-name
              (aget (.-touches (.-originalEvent xy-obj)) 0)))

(defn mousemove-handler [in-chan jqevent]
  (if (pos? (if (nil? (.-buttons jqevent))
              (.-which jqevent)
              (.-buttons jqevent)))
    (xy-message in-chan :draw jqevent)
    (put! in-chan [:drawend])))

(defn nice-mouse-event-capture [in-chan selector end-handler]
  (bind ($ selector) "mousemove" #(mousemove-handler in-chan %))
  (bind ($ selector) "mousedown" #(xy-message in-chan :draw %))
  (bind ($ selector) "mouseup"   end-handler))

(defn draw-event-capture [in-chan selector]
  (let [end-handler (fn [_] (put! in-chan [:drawend]) )]
    (nice-mouse-event-capture in-chan selector end-handler)
    in-chan
))

(defn get-drawing [input-chan out-chan]
  (go (loop [msg (<! input-chan)]
        ;(.log js/console (str "get-drawing: " (first msg)))
        (put! out-chan msg)
        (when (= (first msg) :draw)
          (recur (<! input-chan))))))

(defn filter-drawing-events [input-chan selector]
  (let [
        out-chan   (chan)
        ]
    (go (loop [[msg-name msg-data :as msg] (<! input-chan)]
        ;(.log js/console (str "...... draw-chan: " msg-name))          
          (when (= msg-name :draw)
            (put! out-chan [:draw msg-data])
            (<! (get-drawing input-chan out-chan))
            )
          (recur (<! input-chan))))
    out-chan))

(defn draw-point [selector color coord {:keys [top left]}]
  (append ($ selector)
          (crate/html [:div {:class (str "point " (name color))
                             :style (str "top: " (- (coord :y) top 5) "px;"
                                         "left: " (- (coord :x) left 5) "px;")}])))

(defn draw-points [selector drawing-chan color]
  (let [offset   (offset ($ selector))
        width    (+ (.width ($ selector)) (offset :left))
        height   (+ (.height ($ selector)) (offset :top))
        in-range #(and (< (offset :top) (% :y) height)
                       (< (offset :left) (% :x) width))]
    (go
     (loop [[msg-name xy-obj] (<! drawing-chan)]
       (when (= msg-name :draw)
         (if (in-range xy-obj)
           (draw-point selector color xy-obj offset)
           (.log js/console "out of range!"))
         (recur (<! drawing-chan)))))))

(comment defn drawing-loop [selector]
  (let [drawing-chan (draw-chan selector)]
    (go
     (loop [[msg-name msg-data] (<! drawing-chan)
            color-i 0]
       (if (= :draw msg-name)
         (<! (draw-points selector
                          drawing-chan
                          (get [:red :green :blue] (mod color-i 3)))))
       (recur (<! drawing-chan) (inc color-i))))))


