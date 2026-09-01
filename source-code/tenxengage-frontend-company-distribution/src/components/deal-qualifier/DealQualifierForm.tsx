import { useEffect, useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Calendar } from "@/components/ui/calendar";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { CalendarIcon, Search, Loader2 } from "lucide-react";
import { format } from "date-fns";
import { cn } from "@/lib/utils";
import { ProductMultiSelect } from "@/components/deal-qualifier/ProductMultiSelect";
import { InvoiceUploadZone } from "@/components/deal-qualifier/InvoiceUploadZone";
import { usePartnerContext } from "@/hooks/useDealQualifier";
import type {
  DealQualifierRequest,
  InvoiceExtractionResponse,
} from "@/types/deal-qualifier.types";

const EMPTY_OPTIONS: string[] = [];

interface DealQualifierFormProps {
  onQualify: (request: DealQualifierRequest) => void;
  isLoading?: boolean;
}

export function DealQualifierForm({
  onQualify,
  isLoading,
}: DealQualifierFormProps) {
  const [dealValue, setDealValue] = useState<string>("");
  const [selectedProducts, setSelectedProducts] = useState<string[]>([]);
  const [customerSegment, setCustomerSegment] = useState<string>("");
  const [closeDate, setCloseDate] = useState<Date | undefined>(undefined);

  const { data: partnerContext } = usePartnerContext();
  const segmentOptions = partnerContext?.customerSegmentOptions ?? EMPTY_OPTIONS;

  // Default to the first option once they load, so the form has a valid value
  // without forcing the user to open the dropdown.
  useEffect(() => {
    const [first] = segmentOptions;
    if (!customerSegment && first !== undefined) {
      setCustomerSegment(first);
    }
  }, [segmentOptions, customerSegment]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!closeDate || selectedProducts.length === 0 || !dealValue) {
      return;
    }

    const numericValue = parseFloat(dealValue.replace(/[,$]/g, ""));
    if (isNaN(numericValue) || numericValue <= 0) return;

    // Region is intentionally not collected from the form: the backend
    // derives the partner's region from their location assignments and a
    // partner seller can only qualify deals in their own region anyway,
    // so any seller-supplied value would be misleading or ignored.
    const request: DealQualifierRequest = {
      dealValue: numericValue,
      productSkus: selectedProducts,
      customerSegment,
      closeDate: closeDate.toISOString(),
    };

    onQualify(request);
  };

  const handleExtracted = (response: InvoiceExtractionResponse) => {
    // Auto-fill deal value
    if (response.totalValue != null) {
      setDealValue(`$${Math.round(response.totalValue).toLocaleString()}`);
    }

    // Auto-fill products from matched SKUs
    const matchedSkus = response.skuMappings
      .filter((m) => m.matchedSku != null)
      .map((m) => m.matchedSku as string);
    if (matchedSkus.length > 0) {
      setSelectedProducts(matchedSkus);
    }

    // Auto-fill customer segment, but only if the extracted value is one of
    // the tenant's configured options — otherwise the backend rule comparison
    // (string equality) will silently fail.
    if (
      response.customerSegment &&
      segmentOptions.includes(response.customerSegment)
    ) {
      setCustomerSegment(response.customerSegment);
    }

    // Auto-fill close date from invoice date
    if (response.invoiceDate) {
      try {
        const parsed = new Date(response.invoiceDate + "T12:00:00");
        if (!isNaN(parsed.getTime())) {
          setCloseDate(parsed);
        }
      } catch {
        // Skip
      }
    }
  };

  const formatCurrency = (value: string) => {
    const numericValue = value.replace(/[^0-9]/g, "");
    if (!numericValue) return "";
    const formatted = parseInt(numericValue).toLocaleString();
    return `$${formatted}`;
  };

  const handleDealValueChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const formatted = formatCurrency(e.target.value);
    setDealValue(formatted);
  };

  const isValid =
    closeDate && selectedProducts.length > 0 && dealValue && customerSegment;

  return (
    <Card>
      <CardContent className="pt-6">
        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Invoice upload */}
          <InvoiceUploadZone onExtracted={handleExtracted} />

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="space-y-2">
              <Label htmlFor="dealValue">Deal Value *</Label>
              <Input
                id="dealValue"
                type="text"
                placeholder="$50,000"
                value={dealValue}
                onChange={handleDealValueChange}
                required
              />
            </div>

            <div className="space-y-2">
              <Label>Product/SKU *</Label>
              <ProductMultiSelect
                selected={selectedProducts}
                onSelectionChange={setSelectedProducts}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="customerSegment">Customer Segment *</Label>
              <Select
                value={customerSegment}
                onValueChange={setCustomerSegment}
                disabled={segmentOptions.length === 0}
              >
                <SelectTrigger id="customerSegment">
                  <SelectValue
                    placeholder={
                      segmentOptions.length === 0
                        ? "No segments configured"
                        : "Select segment"
                    }
                  />
                </SelectTrigger>
                <SelectContent>
                  {segmentOptions.map((option) => (
                    <SelectItem key={option} value={option}>
                      {option}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="close-date">Expected Close Date *</Label>
              <Popover>
                <PopoverTrigger asChild>
                  <Button
                    id="close-date"
                    variant="outline"
                    className={cn(
                      "w-full justify-start text-left font-normal",
                      !closeDate && "text-muted-foreground",
                    )}
                  >
                    <CalendarIcon className="mr-2 h-4 w-4" />
                    {closeDate ? (
                      format(closeDate, "PPP")
                    ) : (
                      <span>Pick a date</span>
                    )}
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="w-auto p-0" align="start">
                  <Calendar
                    mode="single"
                    selected={closeDate}
                    onSelect={setCloseDate}
                    initialFocus
                    className="pointer-events-auto"
                  />
                </PopoverContent>
              </Popover>
            </div>
          </div>

          <Button
            type="submit"
            className="w-full"
            size="lg"
            disabled={!isValid || isLoading}
          >
            {isLoading ? (
              <>
                <Loader2 className="mr-2 h-5 w-5 animate-spin" />
                Evaluating...
              </>
            ) : (
              <>
                <Search className="mr-2 h-5 w-5" />
                Check Eligibility
              </>
            )}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
