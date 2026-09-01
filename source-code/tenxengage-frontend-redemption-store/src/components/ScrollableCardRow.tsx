import React, { useState, useRef, useEffect } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";

interface ScrollableCardRowProps {
  icon: React.ReactNode;
  title: string;
  children: React.ReactNode;
}

export function ScrollableCardRow({
  icon,
  title,
  children,
}: ScrollableCardRowProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(true);

  const handleScroll = () => {
    if (!scrollRef.current) return;
    const { scrollLeft, scrollWidth, clientWidth } = scrollRef.current;
    setCanScrollLeft(scrollLeft > 10);
    setCanScrollRight(scrollLeft + clientWidth < scrollWidth - 10);
  };

  const scrollRight = () => {
    scrollRef.current?.scrollBy({ left: 320, behavior: "smooth" });
  };

  const scrollLeft = () => {
    scrollRef.current?.scrollBy({ left: -320, behavior: "smooth" });
  };

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    handleScroll();
    el.addEventListener("scroll", handleScroll);
    return () => el.removeEventListener("scroll", handleScroll);
  }, []);

  return (
    <div>
      <div className="flex items-center gap-2 mb-4">
        {icon}
        <h4 className="text-sm font-semibold text-foreground uppercase tracking-wide">
          {title}
        </h4>
      </div>
      <div className="relative">
        <div
          ref={scrollRef}
          className="flex gap-4 overflow-x-auto pb-2"
          style={{ scrollbarWidth: "none", msOverflowStyle: "none" }}
        >
          {children}
        </div>
        {canScrollLeft && (
          <button
            onClick={scrollLeft}
            aria-label="Scroll left"
            className="absolute left-0 top-0 bottom-2 w-16 flex items-center justify-start bg-gradient-to-r from-card via-card/80 to-transparent cursor-pointer group"
          >
            <div className="flex items-center gap-1 pl-1">
              <ChevronLeft className="h-4 w-4 text-primary animate-pulse" />
              <span className="text-xs font-medium text-primary opacity-80 group-hover:opacity-100">
                Back
              </span>
            </div>
          </button>
        )}
        {canScrollRight && (
          <button
            onClick={scrollRight}
            aria-label="Scroll right"
            className="absolute right-0 top-0 bottom-2 w-16 flex items-center justify-end bg-gradient-to-l from-card via-card/80 to-transparent cursor-pointer group"
          >
            <div className="flex items-center gap-1 pr-1">
              <span className="text-xs font-medium text-primary opacity-80 group-hover:opacity-100">
                More
              </span>
              <ChevronRight className="h-4 w-4 text-primary animate-pulse" />
            </div>
          </button>
        )}
      </div>
    </div>
  );
}
