import { useState, useMemo, useRef, useEffect } from "react";
import { useBuilder } from "@/contexts/BuilderContext";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import {
  Search,
  X,
  CheckCircle2,
  Info,
  ChevronDown,
  Check,
  Loader2,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useLmsCourses, useLmsCourseCategories } from "@/hooks/useLmsCourseApi";
import type {
  TrainingCourseAssignment,
  LmsCourse,
} from "@/types/incentive.types";

export function TrainingCourseEditor() {
  const { state, dispatch } = useBuilder();
  const selectedCourses = state.criteria.trainingCourses;
  const requiredCount = state.criteria.trainingRequiredCount;

  const [search, setSearch] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("all");
  const [popoverOpen, setPopoverOpen] = useState(false);
  const searchInputRef = useRef<HTMLInputElement>(null);

  const { data: allCourses = [], isLoading: coursesLoading } = useLmsCourses();
  const { data: categoryNames = [] } = useLmsCourseCategories();

  const selectedCourseIds = selectedCourses.map((c) => c.courseId);

  // Sync requiredCount with selection
  useEffect(() => {
    if (requiredCount > selectedCourseIds.length) {
      dispatch({
        type: "UPDATE_CRITERIA",
        payload: { trainingRequiredCount: selectedCourseIds.length },
      });
    }
    if (selectedCourseIds.length > 0 && requiredCount === 0) {
      dispatch({
        type: "UPDATE_CRITERIA",
        payload: { trainingRequiredCount: selectedCourseIds.length },
      });
    }
  }, [selectedCourseIds.length, requiredCount, dispatch]);

  useEffect(() => {
    if (popoverOpen) {
      setTimeout(() => searchInputRef.current?.focus(), 50);
    } else {
      setSearch("");
    }
  }, [popoverOpen]);

  // Group courses by category and apply search/filter
  const filteredCategories = useMemo(() => {
    const lowerSearch = search.toLowerCase();
    const grouped = new Map<string, LmsCourse[]>();
    for (const course of allCourses) {
      if (!grouped.has(course.category)) grouped.set(course.category, []);
      grouped.get(course.category)!.push(course);
    }
    return Array.from(grouped.entries())
      .filter(([cat]) => categoryFilter === "all" || cat === categoryFilter)
      .map(([cat, courses]) => ({
        name: cat,
        courses: courses.filter((c) => {
          if (!search) return true;
          return (
            c.name.toLowerCase().includes(lowerSearch) ||
            c.description.toLowerCase().includes(lowerSearch) ||
            (c.provider ?? "").toLowerCase().includes(lowerSearch)
          );
        }),
      }))
      .filter((cat) => cat.courses.length > 0);
  }, [allCourses, search, categoryFilter]);

  function toggleCourse(course: LmsCourse) {
    if (selectedCourseIds.includes(course.id)) {
      dispatch({
        type: "UPDATE_CRITERIA",
        payload: {
          trainingCourses: selectedCourses.filter(
            (c) => c.courseId !== course.id,
          ),
        },
      });
    } else {
      const assignment: TrainingCourseAssignment = {
        courseId: course.id,
        courseName: course.name,
        courseCategory: course.category,
        required: true,
      };
      dispatch({
        type: "UPDATE_CRITERIA",
        payload: { trainingCourses: [...selectedCourses, assignment] },
      });
    }
  }

  const selectedCourseDetails = allCourses.filter((c) =>
    selectedCourseIds.includes(c.id),
  );

  return (
    <div className="space-y-4">
      {/* Course Picker Popover */}
      <div className="space-y-2">
        <Label className="text-sm font-medium">Add Courses</Label>
        <Popover
          open={popoverOpen}
          onOpenChange={(open) => {
            if (!open) {
              setPopoverOpen(false);
              setSearch("");
            }
          }}
        >
          <PopoverTrigger asChild>
            <div
              className="relative"
              onClick={(e) => {
                e.preventDefault();
                setPopoverOpen(true);
                setTimeout(() => searchInputRef.current?.focus(), 0);
              }}
            >
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none" />
              <Input
                ref={searchInputRef}
                placeholder="Search and select courses..."
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  if (!popoverOpen) setPopoverOpen(true);
                }}
                onClick={(e) => e.stopPropagation()}
                onFocus={() => {
                  if (!popoverOpen) setPopoverOpen(true);
                }}
                className="pl-9 pr-8 h-10 text-sm cursor-text"
              />
              {search ? (
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    setSearch("");
                  }}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 p-0.5 rounded-full hover:bg-muted transition-colors"
                >
                  <X className="h-3.5 w-3.5 text-muted-foreground" />
                </button>
              ) : (
                <ChevronDown className="absolute right-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground opacity-50 pointer-events-none" />
              )}
            </div>
          </PopoverTrigger>
          <PopoverContent
            className="w-[var(--radix-popover-trigger-width)] p-0"
            align="start"
            onOpenAutoFocus={(e) => e.preventDefault()}
          >
            {/* Category tabs */}
            <div className="p-2 border-b border-border">
              <div className="flex gap-1 flex-wrap">
                <button
                  type="button"
                  onClick={() => setCategoryFilter("all")}
                  className={cn(
                    "px-2 py-0.5 rounded-full text-xs font-medium transition-colors",
                    categoryFilter === "all"
                      ? "bg-primary text-primary-foreground"
                      : "bg-muted text-muted-foreground hover:bg-muted/80",
                  )}
                >
                  All
                </button>
                {categoryNames.map((name) => (
                  <button
                    key={name}
                    type="button"
                    onClick={() => setCategoryFilter(name)}
                    className={cn(
                      "px-2 py-0.5 rounded-full text-xs font-medium transition-colors",
                      categoryFilter === name
                        ? "bg-primary text-primary-foreground"
                        : "bg-muted text-muted-foreground hover:bg-muted/80",
                    )}
                  >
                    {name}
                  </button>
                ))}
              </div>
            </div>

            {/* Course list */}
            <div className="overflow-y-auto max-h-[300px]">
              {coursesLoading ? (
                <div className="py-8 flex justify-center">
                  <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                </div>
              ) : filteredCategories.length === 0 ? (
                <div className="py-8 text-center text-sm text-muted-foreground">
                  No courses found
                </div>
              ) : (
                <div className="p-1">
                  {filteredCategories.map((category) => (
                    <div key={category.name}>
                      <div className="px-2 py-1.5">
                        <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                          {category.name}
                        </span>
                      </div>
                      {category.courses.map((course) => {
                        const isSelected = selectedCourseIds.includes(
                          course.id,
                        );
                        return (
                          <button
                            key={course.id}
                            type="button"
                            onClick={() => toggleCourse(course)}
                            className={cn(
                              "w-full text-left rounded-md px-2 py-2 flex items-start gap-2 transition-colors",
                              isSelected
                                ? "bg-primary/10"
                                : "hover:bg-muted/60",
                            )}
                          >
                            <div className="pt-0.5 shrink-0">
                              {isSelected ? (
                                <Check className="h-3.5 w-3.5 text-primary" />
                              ) : (
                                <div className="h-3.5 w-3.5 rounded border border-muted-foreground/30" />
                              )}
                            </div>
                            <div className="flex-1 min-w-0">
                              <span className="text-sm font-medium text-foreground truncate">
                                {course.name}
                              </span>
                              <span className="text-xs text-muted-foreground mt-0.5">
                                {course.category}
                              </span>
                            </div>
                          </button>
                        );
                      })}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </PopoverContent>
        </Popover>
      </div>

      {/* Selected Courses + Completion Threshold */}
      {selectedCourseIds.length > 0 && (
        <Card className="border-primary/30 bg-primary/5">
          <CardContent className="py-4 space-y-4">
            <div className="space-y-2">
              <Label className="text-sm font-semibold flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4 text-primary" />
                Selected Courses ({selectedCourseIds.length})
              </Label>
              <div className="space-y-1.5">
                {selectedCourseDetails.map((course) => (
                  <div
                    key={course.id}
                    className="flex items-center justify-between gap-2 rounded-md border border-border bg-card p-2.5"
                  >
                    <div className="flex-1 min-w-0">
                      <span className="text-sm font-medium text-foreground truncate">
                        {course.name}
                      </span>
                      <span className="text-xs text-muted-foreground mt-0.5">
                        {course.category}
                      </span>
                    </div>
                    <button
                      type="button"
                      onClick={() => toggleCourse(course)}
                      className="shrink-0 p-1 rounded-full hover:bg-muted-foreground/20 transition-colors"
                    >
                      <X className="h-3.5 w-3.5 text-muted-foreground" />
                    </button>
                  </div>
                ))}
              </div>
            </div>

            {/* Completion threshold */}
            <div className="flex items-center gap-3 pt-1 border-t border-primary/20">
              <div className="flex items-center gap-2 flex-1">
                <Label className="text-sm font-medium whitespace-nowrap">
                  Courses required to earn reward:
                </Label>
                <TooltipProvider>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Info className="h-3.5 w-3.5 text-muted-foreground cursor-help" />
                    </TooltipTrigger>
                    <TooltipContent side="top" className="max-w-[250px]">
                      <p className="text-xs">
                        Set how many of the selected courses a partner must
                        complete to earn the incentive reward. Set equal to
                        total for "complete all" requirement.
                      </p>
                    </TooltipContent>
                  </Tooltip>
                </TooltipProvider>
              </div>
              <Select
                value={String(requiredCount)}
                onValueChange={(v) =>
                  dispatch({
                    type: "UPDATE_CRITERIA",
                    payload: { trainingRequiredCount: parseInt(v) },
                  })
                }
              >
                <SelectTrigger className="w-24 h-9">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {Array.from(
                    { length: selectedCourseIds.length },
                    (_, i) => i + 1,
                  ).map((n) => (
                    <SelectItem key={n} value={String(n)}>
                      {n === selectedCourseIds.length ? `All ${n}` : `Any ${n}`}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <span className="text-sm text-muted-foreground whitespace-nowrap">
                of {selectedCourseIds.length}
              </span>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
