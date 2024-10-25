import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { TableBodyDirective } from './table-body.directive';
import { TableCaptionDirective } from './table-caption.directive';
import { TableCellDirective } from './table-cell.directive';
import { TableFooterDirective } from './table-footer.directive';
import { TableHeaderDirective } from './table-header.directive';
import { TableHeadDirective } from './table-head.directive';
import { TableRowDirective } from './table-row.directive';
import { TableComponent } from './table.component';

type CustomArgs = {
  invoices: {
    invoice: string;
    paymentStatus: string;
    totalAmount: string;
    paymentMethod: string;
  }[];
};

const meta: Meta<CustomArgs> = {
  component: TableComponent,
  decorators: [
    moduleMetadata({
      imports: [TableBodyDirective, TableCaptionDirective, TableCellDirective, TableFooterDirective, TableHeaderDirective, TableHeadDirective, TableRowDirective]
    })
  ],
  tags: ['autodocs'],
  args: {
    invoices: [
      {
        invoice: 'INV001',
        paymentStatus: 'Paid',
        totalAmount: '$250.00',
        paymentMethod: 'Credit Card'
      },
      {
        invoice: 'INV002',
        paymentStatus: 'Pending',
        totalAmount: '$150.00',
        paymentMethod: 'PayPal'
      },
      {
        invoice: 'INV003',
        paymentStatus: 'Unpaid',
        totalAmount: '$350.00',
        paymentMethod: 'Bank Transfer'
      },
      {
        invoice: 'INV004',
        paymentStatus: 'Paid',
        totalAmount: '$450.00',
        paymentMethod: 'Credit Card'
      },
      {
        invoice: 'INV005',
        paymentStatus: 'Paid',
        totalAmount: '$550.00',
        paymentMethod: 'PayPal'
      },
      {
        invoice: 'INV006',
        paymentStatus: 'Pending',
        totalAmount: '$200.00',
        paymentMethod: 'Bank Transfer'
      },
      {
        invoice: 'INV007',
        paymentStatus: 'Unpaid',
        totalAmount: '$300.00',
        paymentMethod: 'Credit Card'
      }
    ]
  }
};

export default meta;
type Story = StoryObj<TableComponent>;

export const Default: Story = {
  render: (args) => ({
    props: args,
    template: `
      <app-table>
        <caption appTableCaption>A list of your recent invoices.</caption>
        <thead appTableHeader>
          <tr appTableRow>
            <th appTableHead class="w-[100px]">Invoice</th>
            <th appTableHead>Status</th>
            <th appTableHead>Method</th>
            <th appTableHead class="text-right">Amount</th>
          </tr>
        </thead>
        <tbody appTableBody>
          <tr appTableRow *ngFor="let invoice of invoices; trackBy: trackByInvoice">
            <td appTableCell class="font-medium">{{ invoice.invoice }}</td>
            <td appTableCell>{{ invoice.paymentStatus }}</td>
            <td appTableCell>{{ invoice.paymentMethod }}</td>
            <td appTableCell class="text-right">{{ invoice.totalAmount }}</td>
          </tr>
        </tbody>
        <tfoot appTableFooter>
          <tr appTableRow>
            <td appTableCell colspan="3">Total</td>
            <td appTableCell class="text-right">$2,500.00</td>
          </tr>
        </tfoot>
      </app-table>
    `
  })
};
